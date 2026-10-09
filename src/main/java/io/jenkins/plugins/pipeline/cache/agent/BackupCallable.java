package io.jenkins.plugins.pipeline.cache.agent;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import hudson.util.DirScanner;
import io.jenkins.plugins.pipeline.cache.s3.S3OutputStream;

import java.io.File;
import java.io.IOException;

import static java.lang.String.format;

/**
 * Creates a tar archive of a given {@link FilePath} and uploads it to S3.
 */
public class BackupCallable extends AbstractMasterToAgentS3Callable {
    private final String key;
    private final String includes;
    private final String excludes;

    /**
     * @param credentials pre-resolved AWS credentials
     * @param region AWS region
     * @param endpoint S3 endpoint (nullable for native AWS S3)
     * @param bucket S3 bucket name
     * @param key the key used for this backup
     * @param includes Ant-Style pattern to include files (if null then <b>**&#47;*.java</b> is used instead).
     * @param excludes Ant-Style pattern to exclude files (if null then no files are excluded).
     */
    public BackupCallable(ResolvedCredentials credentials, String region, String endpoint, String bucket, String key, String includes, String excludes) {
        super(credentials, region, endpoint, bucket);
        this.key = key;
        this.includes = includes == null ? "**/*" : includes;
        this.excludes = excludes;
    }

    @Override
    public Result invoke(File path, VirtualChannel channel) throws IOException, InterruptedException {
        // make sure that path exists
        if (!path.exists()) {
            return new ResultBuilder()
                    .withInfo("Cache not saved (path not exists)")
                    .build();
        }

        // make sure that path is a directory
        else if (!path.isDirectory()) {
            return new ResultBuilder()
                    .withInfo("Cache not saved (path is not a directory)")
                    .build();
        }

        // make sure that cache not exists yet
        if (cacheItemRepository().exists(key)) {
            return new ResultBuilder()
                    .withInfo(format("Cache not saved (%s already exists)", key))
                    .build();
        }

        // do backup — stream tar archive directly to S3
        long start = System.nanoTime();
        long uploadedBytes;
        boolean success = false;
        S3OutputStream outToS3 = cacheItemRepository().createObjectOutputStream(key);
        try {
            new FilePath(path).tar(outToS3, new DirScanner.Glob(includes, excludes, false));
            uploadedBytes = outToS3.getBytesWritten();
            success = true;
        } finally {
            // abort on failure, otherwise the partial tar would be committed
            if (success) {
                outToS3.close();
            } else {
                outToS3.abort();
            }
        }

        return new ResultBuilder()
                .withInfo(format("Cache saved successfully (%s)", key))
                .withInfo(performanceString(uploadedBytes, start))
                .build();
    }

}
