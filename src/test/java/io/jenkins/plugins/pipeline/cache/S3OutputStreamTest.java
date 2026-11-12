package io.jenkins.plugins.pipeline.cache;

import io.jenkins.plugins.pipeline.cache.s3.CacheItemRepository;
import io.jenkins.plugins.pipeline.cache.s3.S3OutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that an aborted upload does not create an object, while a closed upload does.
 */
@Testcontainers
public class S3OutputStreamTest {

    @Container
    static final S3MockContainer s3mock = new S3MockContainer();

    private String bucket;
    private CacheItemRepository repository;

    @BeforeEach
    public void setUp() {
        bucket = UUID.randomUUID().toString();
        s3mock.createBucket(bucket);
        repository = new CacheItemRepository(
                s3mock.accessKey(),
                s3mock.secretKey(),
                "us-east-1",
                s3mock.getExternalAddress(),
                bucket);
    }

    @Test
    public void abortDoesNotCreateObject() throws Exception {
        String key = "aborted-" + UUID.randomUUID();
        try (S3OutputStream out = repository.createObjectOutputStream(key)) {
            out.write("partial data".getBytes());
            out.abort();
        }
        assertFalse(s3mock.containsKey(bucket, key));
    }

    @Test
    public void abortDoesNotCreateLargeObject() throws Exception {
        // above the 10 MB multipart threshold, so the multipart upload path is covered
        String key = "aborted-large-" + UUID.randomUUID();
        byte[] chunk = new byte[1024 * 1024];
        try (S3OutputStream out = repository.createObjectOutputStream(key)) {
            for (int i = 0; i < 11; i++) {
                out.write(chunk);
            }
            out.abort();
        }
        assertFalse(s3mock.containsKey(bucket, key));
    }

    @Test
    public void closeCreatesObject() throws Exception {
        String key = "committed-" + UUID.randomUUID();
        try (S3OutputStream out = repository.createObjectOutputStream(key)) {
            out.write("full data".getBytes());
        }
        assertTrue(s3mock.containsKey(bucket, key));
    }

    @Test
    public void abortIsIdempotent() throws Exception {
        String key = "aborted-twice-" + UUID.randomUUID();
        S3OutputStream out = repository.createObjectOutputStream(key);
        out.write("partial data".getBytes());
        out.abort();
        out.abort();
        out.close();
        assertFalse(s3mock.containsKey(bucket, key));
    }
}
