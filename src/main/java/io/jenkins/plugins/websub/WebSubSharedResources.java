package io.jenkins.plugins.websub;

import io.jenkins.plugins.websub.subscriber.WebSubSubscriber;
import io.jenkins.plugins.websub.subscriber.WebSubSubscriptionRegistry;
import jenkins.model.JenkinsLocationConfiguration;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import lombok.Setter;
import lombok.val;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.jenkins.plugins.websub.WebSubUtils.fmt;

/**
 * Global shared resources for the plugin.
 *
 * Performs lazy initialization of resources.
 */
class WebSubSharedResources {
    private static Logger logger = LoggerFactory.getLogger(WebSubSharedResources.class);

    private WebSubSharedResources() {}
    private static WebSubSharedResources instance;
    public static WebSubSharedResources getInstance() {
        if (instance == null)
            instance = new WebSubSharedResources();
        return instance;
    }

    /**
     * Represents a failure in current configuration for the instance.
     */
    public static class WebSubConfigurationException extends Exception {
        public WebSubConfigurationException(final String message) {
            super(message);
        }
    }

    private WebSubSubscriber client;
    @Getter private WebSubSubscriptionRegistry registry = new WebSubSubscriptionRegistry();
    // Map from id to job name, maintained by ItemListener.
    @Getter private Map<String, String> jobMap = new HashMap<>();
    // Prefix used after jenkins URL. Should not have leading '/'.
    @Setter private String prefix;

    private String jenkinsUrl;

    /**
     * Retrieve the subscriber client.
     * @return the client
     * @throws Exception on invalid Jenkins URL.
     */
    WebSubSubscriber getClient() throws Exception {
        // We may have just started up and have existing subscriptions.
        // Or we may have not started up but
        val config = JenkinsLocationConfiguration.get();
        if (config == null) {
            throw new Exception("Jenkins configuration not available.");
        }
        val baseUrl = config.getUrl();
        if (baseUrl == null) {
            throw new Exception("Jenkins URL must be configured.");
        } else if (jenkinsUrl == null) {
            // TODO: Gracefully handle existing subscriptions that were loaded from
            //  disk. Might handle this at init time instead.
            jenkinsUrl = baseUrl;
        } else if (!jenkinsUrl.equals(baseUrl)) {
            // TODO: Gracefully handle client configuration update so we don't
            //  lose subscriptions that were pending.
            jenkinsUrl = baseUrl;
            client = null;
        }
        if (client == null) {
            client = new WebSubTriggerSubscriber(getRegistry(), fmt("{}{}", jenkinsUrl, prefix));
        }
        return client;
    }
}
