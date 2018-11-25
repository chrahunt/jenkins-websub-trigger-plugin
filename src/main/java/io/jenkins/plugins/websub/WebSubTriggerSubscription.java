package io.jenkins.plugins.websub;

import static com.google.common.base.Preconditions.checkNotNull;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;

/**
 * Data object representing an individual subscription saved to a Job.
 */
@ToString
public class WebSubTriggerSubscription
        extends AbstractDescribableImpl<WebSubTriggerSubscription> {
    @Extension public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static class DescriptorImpl extends Descriptor<WebSubTriggerSubscription> {
        @Nonnull
        @Override
        public String getDisplayName() { return ""; }
    }

    @Getter private final String topicUrl;
    // Non-persisted id mapping to a subscription.
    @Nullable @Getter @Setter transient String id;

    @DataBoundConstructor
    public WebSubTriggerSubscription(String topicUrl) {
        this.topicUrl = checkNotNull(topicUrl, "Topic URL");
    }
}
