package io.jenkins.plugins.pipeline.cache;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import io.jenkins.plugins.pipeline.cache.agent.ResolvedCredentials;
import io.jenkins.plugins.pipeline.cache.s3.CacheItemRepository;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;

/**
 * Global cache configuration.
 */
@Extension
@Symbol("pipeline-cache")
public class CacheConfiguration extends GlobalConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Identifier of a stored "username with password" credential whose username acts as the
     * AWS access key ID and whose password acts as the secret access key, or {@code null}/blank
     * to fall back to the explicit fields or the default AWS credential provider chain.
     */
    private String credentialsId;
    private String username;
    private Secret password;
    private String bucket;
    private String region;
    private String endpoint;
    private long threshold;

    public CacheConfiguration() {
        load();
    }

    public static CacheConfiguration get() {
        return ExtensionList.lookupSingleton(CacheConfiguration.class);
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = trimToNull(credentialsId);
        save();
    }

    public String getUsername() {
        return username;
    }

    @DataBoundSetter
    public void setUsername(String username) {
        this.username = username;
        save();
    }

    public Secret getPassword() {
        return password;
    }

    @DataBoundSetter
    public void setPassword(Secret password) {
        this.password = password;
        save();
    }

    public String getBucket() {
        return bucket;
    }

    @DataBoundSetter
    public void setBucket(String bucket) {
        this.bucket = bucket;
        save();
    }

    public String getRegion() {
        return region;
    }

    @DataBoundSetter
    public void setRegion(String region) {
        this.region = region;
        save();
    }

    public String getEndpoint() {
        return endpoint;
    }

    @DataBoundSetter
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
        save();
    }

    public long getThreshold() {
        return threshold;
    }

    /**
     * @param threshold threshold in megabyte when the system removes last recently used item from the cache
     */
    @DataBoundSetter
    public void setThreshold(long threshold) {
        this.threshold = threshold;
        save();
    }

    public FormValidation doCheckThreshold(@QueryParameter String value) {
        try {
            Integer.parseInt(value);
            return FormValidation.ok();
        } catch (NumberFormatException e) {
            return FormValidation.error("Not an integer");
        }
    }

    /**
     * Populates the credentials selector with all stored "username with password" credentials
     * visible in the current security context.
     */
    public ListBoxModel doFillCredentialsIdItems() {
        StandardListBoxModel result = new StandardListBoxModel();
        List<? extends StandardUsernamePasswordCredentials> credentials =
                CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, Jenkins.get());
        result.withAll(credentials);
        return result.includeCurrentValue(trimToNull(credentialsId));
    }

    /**
     * Resolves AWS credentials on the controller. Priority order:
     * <ol>
     *   <li>a stored Jenkins credential ({@code credentialsId}) — its username becomes the
     *   access key ID and its password the secret access key;</li>
     *   <li>explicit username/password configured in this form; otherwise</li>
     *   <li>the default AWS credential provider chain (instance role, environment variables,
     *   profile files).</li>
     * </ol>
     * The resolved credentials are safe to serialize to build agents.
     */
    public ResolvedCredentials resolveCredentials() {
        if (credentialsId != null && !credentialsId.isBlank()) {
            return resolveStoredCredentials(credentialsId);
        }
        if (username != null && !username.isBlank() && password != null) {
            return new ResolvedCredentials(username, password.getPlainText());
        }
        return resolveDefaultChain();
    }

    /**
     * Looks up a stored credential by id and converts it to resolved credentials.
     *
     * @throws IllegalStateException if the credential does not exist or is not a "username with password" type
     */
    static ResolvedCredentials resolveStoredCredentials(String id) {
        List<? extends StandardUsernamePasswordCredentials> candidates =
                CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, Jenkins.get());
        StandardUsernamePasswordCredentials match = null;
        for (StandardUsernamePasswordCredentials candidate : candidates) {
            if (Objects.equals(candidate.getId(), id)) {
                match = candidate;
                break;
            }
        }
        if (match == null) {
            throw new IllegalStateException("Stored credential '" + id
                    + "' not found or not a username-with-password credential");
        }
        return new ResolvedCredentials(match.getUsername(), match.getPassword().getPlainText());
    }

    /**
     * Falls back to the default AWS credential provider chain (instance role, environment
     * variables, etc.). Resolution happens on the controller, so agents do not need access
     * to the local credential chain.
     */
    private static ResolvedCredentials resolveDefaultChain() {
        try (DefaultCredentialsProvider provider = DefaultCredentialsProvider.create()) {
            AwsCredentials awsCreds = provider.resolveCredentials();
            String sessionToken = null;
            if (awsCreds instanceof AwsSessionCredentials sessionCreds) {
                sessionToken = sessionCreds.sessionToken();
            }
            return new ResolvedCredentials(awsCreds.accessKeyId(), awsCreds.secretAccessKey(), sessionToken);
        }
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @POST
    public FormValidation doTestConnection(
            @QueryParameter String credentialsId,
            @QueryParameter String username,
            @QueryParameter String password,
            @QueryParameter String bucket,
            @QueryParameter String region,
            @QueryParameter String endpoint) {
        Objects.requireNonNull(Jenkins.get()).checkPermission(Jenkins.ADMINISTER);

        try {
            ResolvedCredentials credentials;
            if (credentialsId != null && !credentialsId.isBlank()) {
                credentials = resolveStoredCredentials(credentialsId.trim());
            } else if (username != null && !username.isBlank()) {
                credentials = new ResolvedCredentials(username, password == null ? "" : password);
            } else {
                credentials = resolveDefaultChain();
            }
            try (var repo = new CacheItemRepository(credentials, region, endpoint, bucket)) {
                if (repo.bucketExists()) {
                    return FormValidation.ok("OK");
                }
                return FormValidation.error("Bucket not exists");
            }
        } catch (Exception e) {
            return FormValidation.error(e.getMessage());
        }
    }

}
