package io.jenkins.plugins.pipeline.cache.s3;

import org.junit.Test;

public class CacheItemRepositoryTest {

    private static final String ASYNC_HTTP_IMPL_PROPERTY = "software.amazon.awssdk.http.async.service.impl";

    @Test
    public void buildsClientsWithoutSdkHttpClientDiscovery() {
        // Makes SDK discovery fail like in Jenkins, where the core plugin can't see the Netty plugin
        System.setProperty(ASYNC_HTTP_IMPL_PROPERTY, "does.not.Exist");
        try (CacheItemRepository ignored = new CacheItemRepository("user", "secret", "us-east-1", "http://localhost:9000", "bucket")) {
            // construction must not rely on SDK discovery
        } finally {
            System.clearProperty(ASYNC_HTTP_IMPL_PROPERTY);
        }
    }
}
