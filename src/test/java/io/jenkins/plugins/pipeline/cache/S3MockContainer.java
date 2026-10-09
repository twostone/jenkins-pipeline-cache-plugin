package io.jenkins.plugins.pipeline.cache;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Testcontainer exposing an S3-compatible endpoint via {@code adobe/s3mock}.
 *
 * <p>S3Mock implements the subset of the S3 API used by this plugin (objects, prefixes,
 * user metadata, copy, multipart) and performs no credential validation, so the arbitrary
 * values returned here satisfy the AWS SDK. Path-style access is used, matching how the
 * plugin's {@code CacheItemRepository} builds its clients.</p>
 */
public class S3MockContainer extends GenericContainer<S3MockContainer> {

    public static final String ACCESS_KEY = "test-access-key";
    public static final String SECRET_KEY = "test-secret-key";

    public S3MockContainer() {
        super("adobe/s3mock:4.11.0");
        setWaitStrategy(Wait.forHttp("/favicon.ico").forStatusCode(200));
        withExposedPorts(9090);
    }

    public String accessKey() {
        return ACCESS_KEY;
    }

    public String secretKey() {
        return SECRET_KEY;
    }

    public String getExternalAddress() {
        return "http://" + getHost() + ":" + getMappedPort(9090);
    }

    /**
     * Creates a bucket on this mock.
     */
    public void createBucket(String bucket) {
        client().createBucket(CreateBucketRequest.builder().bucket(bucket).build());
    }

    /**
     * Returns true if an object with the given key exists in the given bucket.
     */
    public boolean containsKey(String bucket, String key) {
        try {
            client().headObject(h -> h.bucket(bucket).key(key));
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    /**
     * Stores an object with the given content and user metadata.
     */
    public void putObject(String bucket, String key, byte[] content, Map<String, String> metadata) {
        try (S3Client client = client()) {
            client.putObject(p -> p.bucket(bucket).key(key).metadata(metadata), RequestBody.fromBytes(content));
        }
    }

    public HeadObjectResponse headObject(String bucket, String key) {
        try (S3Client client = client()) {
            return client.headObject(h -> h.bucket(bucket).key(key));
        }
    }

    /**
     * Sets the last access metadata of an object to 0, so that the next update is not skipped by the time threshold.
     */
    public void resetLastAccess(String bucket, String key) {
        try (S3Client client = client()) {
            Map<String, String> metadata = new HashMap<>(client.headObject(h -> h.bucket(bucket).key(key)).metadata());
            metadata.put("last_access", "0");
            client.copyObject(c -> c.sourceBucket(bucket).sourceKey(key)
                    .destinationBucket(bucket).destinationKey(key)
                    .metadataDirective(MetadataDirective.REPLACE)
                    .metadata(metadata));
        }
    }

    private S3Client client() {
        return S3Client.builder()
                .forcePathStyle(true)
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(getExternalAddress()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey(), secretKey())))
                .build();
    }
}
