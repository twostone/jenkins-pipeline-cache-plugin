package io.jenkins.plugins.pipeline.cache.s3;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import io.jenkins.plugins.pipeline.cache.agent.ResolvedCredentials;

import java.net.URI;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class CacheItemRepository implements AutoCloseable {

    static final String LAST_ACCESS = "last_access";
    static final String CREATION = "creation";
    private static final long TIME_THRESHOLD = 5 * 60 * 1000L; // 5 minutes

    private final S3Client s3;
    private final S3AsyncClient s3Async;
    private final String bucket;

    private static final String DEFAULT_S3_ENDPOINT = "https://s3.amazonaws.com";

    public CacheItemRepository(String username, String password, String region, String endpoint, String bucket) {
        this(StaticCredentialsProvider.create(AwsBasicCredentials.create(username, password)), region, endpoint, bucket);
    }

    public CacheItemRepository(ResolvedCredentials credentials, String region, String endpoint, String bucket) {
        this(toCredentialsProvider(credentials), region, endpoint, bucket);
    }

    public CacheItemRepository(AwsCredentialsProvider credentialsProvider, String region, String endpoint, String bucket) {
        this.s3 = createS3Client(credentialsProvider, endpoint, region);
        this.s3Async = createS3AsyncClient(credentialsProvider, endpoint, region);
        this.bucket = bucket;
    }

    private static AwsCredentialsProvider toCredentialsProvider(ResolvedCredentials credentials) {
        if (credentials.hasSessionToken()) {
            return StaticCredentialsProvider.create(
                    AwsSessionCredentials.create(credentials.getAccessKeyId(), credentials.getSecretAccessKey(), credentials.getSessionToken()));
        }
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(credentials.getAccessKeyId(), credentials.getSecretAccessKey()));
    }

    private static boolean isCustomEndpoint(String endpoint) {
        return endpoint != null && !endpoint.isBlank() && !endpoint.equals(DEFAULT_S3_ENDPOINT);
    }

    protected S3Client createS3Client(AwsCredentialsProvider credentialsProvider, String endpoint, String region) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider);
        if (isCustomEndpoint(endpoint)) {
            builder.forcePathStyle(true).endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    protected S3AsyncClient createS3AsyncClient(AwsCredentialsProvider credentialsProvider, String endpoint, String region) {
        S3AsyncClientBuilder builder = S3AsyncClient.builder()
                .multipartEnabled(true)
                .multipartConfiguration(cfg -> cfg
                        .minimumPartSizeInBytes(S3OutputStream.DEFAULT_PART_SIZE)
                        .thresholdInBytes(S3OutputStream.DEFAULT_PART_SIZE))
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider);
        if (isCustomEndpoint(endpoint)) {
            builder.forcePathStyle(true).endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    /**
     * Provides the total size of all cache items.
     */
    public long getTotalCacheSize() {
        return s3.listObjectsV2Paginator(builder -> builder.bucket(bucket))
                .stream()
                .flatMap(r -> r.contents().stream())
                .map(S3Object::size)
                .reduce(Long::sum).orElse(0L);
    }

    /**
     * Provides a stream of all cache items.
     */
    public Stream<CacheItem> findAll() {
        return s3.listObjectsV2Paginator(builder -> builder.bucket(bucket))
                .stream()
                .flatMap(r -> r.contents().stream())
                .map(this::mapToCacheItem);
    }

    /**
     * Removes items from the cache.
     *
     * @param keys Stream of keys which should be removed
     * @return count of removed items
     */
    public int delete(Stream<String> keys) {
        List<String> keyList = keys.toList();
        // Fire all deletes concurrently via the async client
        List<? extends CompletableFuture<?>> futures = keyList.stream()
                .map(key -> s3Async.deleteObject(b -> b.bucket(bucket).key(key)))
                .toList();
        futures.forEach(CompletableFuture::join);
        return keyList.size();
    }

    /**
     * Provides the size of a cache item in byte.
     */
    public long getContentLength(String key) {
        return s3.headObject(builder -> builder.bucket(bucket).key(key)).contentLength();
    }

    /**
     * Provides the {@link ResponseInputStream} assigned to a given key or null if it not exists.
     */
    public ResponseInputStream<GetObjectResponse> getS3Object(String key) {
        return s3.getObject(builder -> builder.bucket(bucket).key(key));
    }

    /**
     * Updates the last access timestamp of a given cache item by key. <b>Note: As a side effect this also changes the last modification
     * timestamp, which means that last modification and last access can be considered as equals</b>
     */
    public void updateLastAccess(String key) {
        HeadObjectResponse head = s3.headObject(builder -> builder.bucket(bucket).key(key));
        Map<String, String> metadata = new HashMap<>(head.metadata());

        long lastAccessTime = Long.parseLong(metadata.getOrDefault(LAST_ACCESS, "0"));
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastAccessTime > TIME_THRESHOLD) {
            metadata.put(LAST_ACCESS, Long.toString(currentTime));

            s3.copyObject(builder ->
                    builder.sourceBucket(bucket)
                            .sourceKey(key)
                            .destinationBucket(bucket)
                            .destinationKey(key)
                            .metadataDirective(MetadataDirective.REPLACE)
                            .metadata(metadata));
        }
    }

    /**
     * Finds the best matching key which can be used to restore an existing cache. It works as follows:
     * <ol>
     *   <li>if key exists then key is returned</li>
     *   <li>if one of the restoreKeys exists then this one is returned</li>
     *   <li>if an existing key starts with one of the restoreKeys then the existing key is returned</li>
     *   <li>otherwise null is returned</li>
     * </ol>
     */
    public String findRestoreKey(String key, String... restoreKeys) {
        if (key != null && exists(key)) {
            // 1.
            return key;
        }

        if (restoreKeys == null) {
            // 4.
            return null;
        }

        for (String restoreKey : restoreKeys) {
            if (exists(restoreKey)) {
                // 2.
                return restoreKey;
            }
        }

        return Arrays.stream(restoreKeys)
                .map(this::findKeyByPrefix)
                .filter(Objects::nonNull)
                // 3.
                .findFirst()
                // 4.
                .orElse(null);
    }

    /**
     * Returns true if the object with the given exists, otherwise false.
     */
    public boolean exists(String key) {
        try {
            s3.headObject(builder -> builder.bucket(bucket).key(key));
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    /**
     * Creates an {@link java.io.OutputStream} for a given key. This can be used to write data directly to a new object in S3.
     */
    public S3OutputStream createObjectOutputStream(String key) {
        return new S3OutputStream(s3Async, bucket, key);
    }

    /**
     * Returns true if the underlying bucket exists, otherwise false.
     */
    public boolean bucketExists() {
        try {
            s3.headBucket(builder -> builder.bucket(bucket));
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public void close() {
        s3.close();
        s3Async.close();
    }

    /**
     * Transforms a {@link S3Object} object into a {@link CacheItem} object.
     */
    private CacheItem mapToCacheItem(S3Object s3Object) {
        return new CacheItem(
                s3Object.key(),
                s3Object.size(),
                // we just use the last modified timestamp here as last access time (assumption: last access and last modified are equals
                // anyway), this saves one extra request (last access timestamp is stored as metadata)
                s3Object.lastModified().toEpochMilli()
        );
    }

    private String findKeyByPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return null;
        }

        List<String> keys = s3.listObjectsV2Paginator(builder -> builder.bucket(bucket).prefix(prefix))
                .stream()
                .flatMap(r -> r.contents().stream())
                .map(S3Object::key)
                .toList();

        if (keys.isEmpty()) {
            return null;
        }

        return keys.stream()
                .map(this::mapToKeyCreation)
                .max(Comparator.comparing(KeyCreation::getCreation))
                .map(KeyCreation::getKey)
                .orElse(null);
    }

    private KeyCreation mapToKeyCreation(String key) {
        HeadObjectResponse head = s3.headObject(builder -> builder.bucket(bucket).key(key));
        return new KeyCreation(key, Long.parseLong(head.metadata().getOrDefault(CREATION, "0")));
    }

    private static class KeyCreation {
        private final String key;
        private final long creation;

        private KeyCreation(String key, long creation) {
            this.key = key;
            this.creation = creation;
        }

        public String getKey() {
            return key;
        }

        public long getCreation() {
            return creation;
        }
    }
}
