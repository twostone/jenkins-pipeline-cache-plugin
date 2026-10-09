package io.jenkins.plugins.pipeline.cache.s3;

import io.jenkins.plugins.pipeline.cache.S3MockContainer;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;

/**
 * Checks that updating the last access timestamp works for objects below and above the multipart threshold.
 */
public class CacheItemRepositoryLastAccessTest {

    @ClassRule
    public static S3MockContainer s3mock = new S3MockContainer();

    private String bucket;
    private CacheItemRepository repository;

    @Before
    public void setUp() {
        bucket = UUID.randomUUID().toString();
        s3mock.createBucket(bucket);
        repository = new CacheItemRepository(s3mock.accessKey(), s3mock.secretKey(), "us-east-1", s3mock.getExternalAddress(), bucket);
    }

    @After
    public void tearDown() {
        repository.close();
    }

    @Test
    public void updatesLastAccessOfSmallObject() {
        String key = putObject(1024);

        repository.updateLastAccess(key);

        assertLastAccessUpdated(key, 1024);
    }

    @Test
    public void updatesLastAccessOfLargeObjectWithMultipartCopy() {
        int size = 11 * 1024 * 1024;
        String key = putObject(size);

        repository.updateLastAccess(key);

        HeadObjectResponse head = assertLastAccessUpdated(key, size);
        // multipart ETags have a part count suffix
        assertThat(head.eTag(), containsString("-"));
    }

    @Test
    public void skipsRecentLastAccess() {
        String key = putObject(1024);
        repository.updateLastAccess(key);
        HeadObjectResponse before = s3mock.headObject(bucket, key);

        repository.updateLastAccess(key);

        HeadObjectResponse after = s3mock.headObject(bucket, key);
        assertEquals(before.eTag(), after.eTag());
        assertEquals(before.lastModified(), after.lastModified());
        assertEquals(before.metadata(), after.metadata());
    }

    private String putObject(int size) {
        String key = UUID.randomUUID().toString();
        s3mock.putObject(bucket, key, new byte[size], Map.of(CacheItemRepository.CREATION, "42", CacheItemRepository.LAST_ACCESS, "0"));
        return key;
    }

    private HeadObjectResponse assertLastAccessUpdated(String key, long size) {
        HeadObjectResponse head = s3mock.headObject(bucket, key);
        assertThat(Long.parseLong(head.metadata().get(CacheItemRepository.LAST_ACCESS)), greaterThan(0L));
        assertThat(head.metadata().get(CacheItemRepository.CREATION), is("42"));
        assertThat(head.contentLength(), is(size));
        return head;
    }
}
