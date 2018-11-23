package io.jenkins.plugins.websub;

import static com.google.common.base.Preconditions.checkNotNull;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;

/**
 * Data object representing an individual subscription saved to a Job.
 */
public class WebSubTriggerSubscription
        extends AbstractDescribableImpl<WebSubTriggerSubscription> {
    @Extension public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static class DescriptorImpl extends Descriptor<WebSubTriggerSubscription> {
        @Nonnull
        @Override
        public String getDisplayName() { return ""; }
    }

    private final String topicUrl;

    @DataBoundConstructor
    public WebSubTriggerSubscription(String topicUrl) {
        this.topicUrl = checkNotNull(topicUrl, "Topic URL");
    }

    @Override
    public String toString() {
        return "WebSubTriggerSubscription [topicUrl="
                + topicUrl + "]";
    }
}
