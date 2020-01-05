package io.jenkins.plugins.websub;

import static com.google.common.base.Preconditions.checkNotNull;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.val;
import org.apache.commons.validator.routines.UrlValidator;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import org.kohsuke.stapler.QueryParameter;

/**
 * Data object representing an individual subscription saved to a Job.
 *
 * A subscription contains:
 * - topicUrl - the URL to retrieve for discovery
 *
 * // TODO
 * - basicAuthCredential - if provided, the credential to use for basic authentication to
 *   the topicUrl and subscription endpoints
 * - headers - list of headers to send in requests to the topicUrl and subscription endpoints
 * - params
 * - cert
 */
@ToString
public class WebSubTriggerSubscription
        extends AbstractDescribableImpl<WebSubTriggerSubscription> {
    @Extension
    @SuppressWarnings("unused") // Used by Jenkins.
    public static class DescriptorImpl extends Descriptor<WebSubTriggerSubscription> {
        @Nonnull
        @Override
        public String getDisplayName() { return ""; }

        @SuppressWarnings("unused") // Used by form validator.
        public FormValidation doCheckTopicUrl(@QueryParameter String value) {
            final String[] validSchemes = {"http", "https"};
            final long options = UrlValidator.ALLOW_LOCAL_URLS + UrlValidator.NO_FRAGMENTS;
            val validator = new UrlValidator(validSchemes, options);
            if (validator.isValid(value)) {
                return FormValidation.ok();
            } else {
                return FormValidation.error("Invalid URL");
            }
        }
    }

    @Getter private final String topicUrl;
    // Non-persisted id mapping to an actual subscription.
    @Nullable @Getter @Setter transient String id;

    @DataBoundConstructor
    public WebSubTriggerSubscription(String topicUrl) {
        this.topicUrl = checkNotNull(topicUrl, "Topic URL");
    }
}
