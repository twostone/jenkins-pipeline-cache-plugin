package io.jenkins.plugins.pipeline.cache;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.util.Secret;
import io.jenkins.plugins.pipeline.cache.agent.ResolvedCredentials;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class StoredCredentialsTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private void addCredential(String id, String username, String password) throws Exception {
        Map<Domain, List<com.cloudbees.plugins.credentials.Credentials>> map = new HashMap<>();
        map.put(Domain.global(),
                List.of(new UsernamePasswordCredentialsImpl(
                        CredentialsScope.GLOBAL, id, "description", username, password)));
        SystemCredentialsProvider.getInstance().setDomainCredentialsMap(map);
    }

    @Test
    public void testResolvesStoredCredential() throws Exception {
        // GIVEN
        addCredential("stored-aws", "AKIA_TEST", "secret-test");
        CacheConfiguration config = CacheConfiguration.get();
        config.setCredentialsId("stored-aws");

        // WHEN
        ResolvedCredentials resolved = config.resolveCredentials();

        // THEN
        assertEquals("AKIA_TEST", resolved.getAccessKeyId());
        assertEquals("secret-test", resolved.getSecretAccessKey());
    }

    @Test
    public void testStoredCredentialTakesPrecedenceOverExplicit() throws Exception {
        // GIVEN
        addCredential("stored-aws", "AKIA_TEST", "secret-test");
        CacheConfiguration config = CacheConfiguration.get();
        config.setCredentialsId("stored-aws");
        config.setUsername("explicit-user");
        config.setPassword(Secret.fromString("explicit-secret"));

        // WHEN
        ResolvedCredentials resolved = config.resolveCredentials();

        // THEN
        assertEquals("AKIA_TEST", resolved.getAccessKeyId());
        assertEquals("secret-test", resolved.getSecretAccessKey());
    }

    @Test
    public void testFallsBackToExplicitWhenNoCredentialSelected() {
        // GIVEN
        CacheConfiguration config = CacheConfiguration.get();
        config.setCredentialsId("");
        config.setUsername("explicit-user");
        config.setPassword(Secret.fromString("explicit-secret"));

        // WHEN
        ResolvedCredentials resolved = config.resolveCredentials();

        // THEN
        assertEquals("explicit-user", resolved.getAccessKeyId());
        assertEquals("explicit-secret", resolved.getSecretAccessKey());
    }

    @Test
    public void testUnknownCredentialIdFails() {
        // GIVEN
        CacheConfiguration config = CacheConfiguration.get();
        config.setCredentialsId("does-not-exist");

        // WHEN / THEN
        try {
            config.resolveCredentials();
            fail("expected IllegalStateException for unknown credential id");
        } catch (IllegalStateException e) {
            assertEquals("Stored credential 'does-not-exist' not found or not a username-with-password credential", e.getMessage());
        }
    }

}
