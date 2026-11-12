package io.jenkins.plugins.pipeline.cache;


import org.htmlunit.html.HtmlForm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ConfigurationTest {

    @RegisterExtension
    JenkinsSessionExtension rule = new JenkinsSessionExtension();

    @Test
    public void testConfiguration() throws Throwable {
        // WHEN
        rule.then(r -> {
            // THEN
            assertNull(CacheConfiguration.get().getUsername(), "should be empty initially");
            assertNull(CacheConfiguration.get().getPassword(), "should be empty initially");
            assertNull(CacheConfiguration.get().getBucket(), "should be empty initially");
            assertNull(CacheConfiguration.get().getRegion(), "should be empty initially");
            assertNull(CacheConfiguration.get().getEndpoint(), "should be empty initially");
            assertEquals(0L, CacheConfiguration.get().getThreshold());

            // WHEN
            try (JenkinsRule.WebClient c = r.createWebClient()) {
                HtmlForm config = c.goTo("manage/configure").getFormByName("config");
                config.getInputByName("_.username").setValueAttribute("alice");
                config.getInputByName("_.password").setValueAttribute("secret");
                config.getInputByName("_.bucket").setValueAttribute("blue");
                config.getInputByName("_.region").setValueAttribute("dc1");
                config.getInputByName("_.endpoint").setValueAttribute("http://localhost:9000");
                config.getInputByName("_.threshold").setValueAttribute(Long.toString(777));
                r.submit(config);
            }

            // THEN
            assertEquals("alice", CacheConfiguration.get().getUsername(), "should be editable");
            assertEquals("secret", CacheConfiguration.get().getPassword().getPlainText(), "should be editable");
            assertEquals("blue", CacheConfiguration.get().getBucket(), "should be editable");
            assertEquals("dc1", CacheConfiguration.get().getRegion(), "should be editable");
            assertEquals("http://localhost:9000", CacheConfiguration.get().getEndpoint(), "should be editable");
            assertEquals(777L, CacheConfiguration.get().getThreshold(), "should be editable");

        });
        // WHEN
        rule.then(r -> {
            // THEN
            assertEquals("alice", CacheConfiguration.get().getUsername(), "should be still there after restart of Jenkins");
            assertEquals("secret", CacheConfiguration.get().getPassword().getPlainText(), "should be still there after restart of Jenkins");
            assertEquals("blue", CacheConfiguration.get().getBucket(), "should be still there after restart of Jenkins");
            assertEquals("dc1", CacheConfiguration.get().getRegion(), "should be still there after restart of Jenkins");
            assertEquals("http://localhost:9000", CacheConfiguration.get().getEndpoint(), "should be still there after restart of Jenkins");
            assertEquals(777L, CacheConfiguration.get().getThreshold(), "should be still there after restart of Jenkins");
        });
    }

}
