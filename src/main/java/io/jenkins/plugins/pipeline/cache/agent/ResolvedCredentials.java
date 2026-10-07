package io.jenkins.plugins.pipeline.cache.agent;

import java.io.Serializable;

/**
 * Holds pre-resolved AWS credentials that can be serialized from the controller to build agents.
 * When using a stored Jenkins credential or the default credential provider chain, credentials are
 * resolved on the controller and the resulting access key, secret key, and optional session
 * token are passed to agents.
 */
public class ResolvedCredentials implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String accessKeyId;
    private final String secretAccessKey;
    private final String sessionToken;

    public ResolvedCredentials(String accessKeyId, String secretAccessKey, String sessionToken) {
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
        this.sessionToken = sessionToken;
    }

    public ResolvedCredentials(String accessKeyId, String secretAccessKey) {
        this(accessKeyId, secretAccessKey, null);
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public boolean hasSessionToken() {
        return sessionToken != null && !sessionToken.isEmpty();
    }
}
