package io.jenkins.plugins.pipeline.cache;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import io.jenkins.plugins.pipeline.cache.agent.ResolvedCredentials;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StoredCredentialsTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private void addCredential(Credentials... credentials) {
        Map<Domain, List<Credentials>> map = new HashMap<>();
        map.put(Domain.global(), List.of(credentials));
        SystemCredentialsProvider.getInstance().setDomainCredentialsMap(map);
    }

    private UsernamePasswordCredentialsImpl usernamePassword(String id, String username, String password)
            throws Exception {
        return new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL, id, "description", username, password);
    }

    @Test
    public void testResolvesStoredCredential() throws Exception {
        // GIVEN
        addCredential(usernamePassword("stored-aws", "AKIA_TEST", "secret-test"));
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
        addCredential(usernamePassword("stored-aws", "AKIA_TEST", "secret-test"));
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
    public void testResolvesAwsCredentialWithStaticKeys() {
        // GIVEN
        addCredential(new FakeAwsCredentials("aws-static",
                AwsBasicCredentials.create("AKIA_AWS", "aws-secret")));
        CacheConfiguration config = CacheConfiguration.get();
        config.setCredentialsId("aws-static");

        // WHEN
        ResolvedCredentials resolved = config.resolveCredentials();

        // THEN
        assertEquals("AKIA_AWS", resolved.getAccessKeyId());
        assertEquals("aws-secret", resolved.getSecretAccessKey());
        assertFalse("no session token expected", resolved.hasSessionToken());
    }

    @Test
    public void testResolvesAwsCredentialPreservingSessionToken() {
        // GIVEN
        addCredential(new FakeAwsCredentials("aws-sts", AwsSessionCredentials.create(
                "AKIA_SESSION", "session-secret", "session-token-123")));
        CacheConfiguration config = CacheConfiguration.get();
        config.setCredentialsId("aws-sts");

        // WHEN
        ResolvedCredentials resolved = config.resolveCredentials();

        // THEN
        assertEquals("AKIA_SESSION", resolved.getAccessKeyId());
        assertEquals("session-secret", resolved.getSecretAccessKey());
        assertTrue(resolved.hasSessionToken());
        assertEquals("session-token-123", resolved.getSessionToken());
    }

    @Test
    public void testUnsupportedCredentialTypeFails() {
        // GIVEN: a standard credential which is neither username/password nor an AWS credential
        FakeUnsupportedCredentials credential = new FakeUnsupportedCredentials();
        addCredential(credential);
        CacheConfiguration config = CacheConfiguration.get();
        config.setCredentialsId(credential.getId());

        // WHEN / THEN
        try {
            config.resolveCredentials();
            fail("expected IllegalStateException for unsupported credential type");
        } catch (IllegalStateException e) {
            assertEquals("Stored credential 'unsupported' is not supported, expected a 'username with password' credential or an AWS credential",
                    e.getMessage());
        }
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
            assertEquals("Stored credential 'does-not-exist' not found", e.getMessage());
        }
    }

    @Test
    public void testFillsCredentialsSelectorWithSupportedTypesOnly() throws Exception {
        // GIVEN: one credential per supported type plus one unsupported one
        addCredential(
                usernamePassword("username-password", "user1", "pass1"),
                new FakeAwsCredentials("aws-static", AwsBasicCredentials.create("AKIA_AWS", "aws-secret")),
                new FakeUnsupportedCredentials());

        // WHEN
        List<ListBoxModel.Option> options =
                new ArrayList<>(CacheConfiguration.get().doFillCredentialsIdItems());

        // THEN
        List<String> values = options.stream().map(o -> o.value).toList();
        assertTrue(values.contains("username-password"));
        assertTrue(values.contains("aws-static"));
        assertFalse("unsupported types must not be offered", values.contains("unsupported"));
    }

    /**
     * Test double for a credential from the
     * <a href="https://github.com/jenkinsci/aws-credentials-plugin">AWS Credentials plugin</a>:
     * a standard credential which additionally implements the SDK v2
     * {@link AwsCredentialsProvider} interface — the interface through which this plugin detects
     * such credentials at runtime (the AWS Credentials plugin exposes that interface on its
     * credentials and does not need to be a dependency of this plugin).
     */
    static class FakeAwsCredentials extends com.cloudbees.plugins.credentials.impl.BaseStandardCredentials
            implements AwsCredentialsProvider {

        private static final long serialVersionUID = 1L;
        private final AwsCredentials credentials;

        FakeAwsCredentials(String id, AwsCredentials credentials) {
            super(CredentialsScope.GLOBAL, id, "fake aws credential");
            this.credentials = credentials;
        }

        @Override
        public AwsCredentials resolveCredentials() {
            return credentials;
        }

        public String getDisplayName() {
            return getId() + " (display)";
        }
    }

    /**
     * Test double for a standard credential the plugin must reject: not a
     * "username with password" credential and not an AWS credential.
     */
    static class FakeUnsupportedCredentials extends com.cloudbees.plugins.credentials.impl.BaseStandardCredentials {

        private static final long serialVersionUID = 1L;

        FakeUnsupportedCredentials() {
            super(CredentialsScope.GLOBAL, "unsupported", "description");
        }
    }

}
