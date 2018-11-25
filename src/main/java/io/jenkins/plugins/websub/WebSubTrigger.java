package io.jenkins.plugins.websub;

import com.google.common.collect.ImmutableMultimap;
import hudson.Extension;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Item;
import hudson.model.Job;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.model.ParameterizedJobMixIn.ParameterizedJob;
import lombok.ToString;
import lombok.Value;
import lombok.val;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static io.jenkins.plugins.websub.WebSubUtils.fmt;
import static io.jenkins.plugins.websub.WebSubUtils.cast;

/**
 * A trigger associated with a Job that represents multiple subscriptions.
 */
@ToString
public class WebSubTrigger extends Trigger<Job<?, ?>> {

    @Symbol("WebSubTrigger")
    public static class WebSubDescriptor extends TriggerDescriptor {
        @Override
        public boolean isApplicable(final Item item) {
            return Job.class.isAssignableFrom(item.getClass());
        }

        @Nonnull
        @Override
        public String getDisplayName() { return "WebSub Trigger"; }
    }

    @Extension
    public static final WebSubDescriptor DESCRIPTOR = new WebSubDescriptor();

    private static final int USE_DEFAULT_QUIET_PERIOD = -1;
    private final List<WebSubTriggerSubscription> subscriptions;

    @DataBoundConstructor
    @SuppressWarnings("unused")
    public WebSubTrigger(final List<WebSubTriggerSubscription> subscriptions) {
        this.subscriptions = subscriptions;
    }

    public List<WebSubTriggerSubscription> getSubscriptions() {
        return subscriptions;
    }

    private static class WebSubCause extends Cause {
        private final WebSubTriggerSubscription subscription;

        public WebSubCause(final WebSubTriggerSubscription subscription) {
            this.subscription = subscription;
        }

        @Override
        public String getShortDescription() {
            return fmt("Triggered by notification from {} (id: {})", subscription.getTopicUrl(), subscription.getId());
        }

        static CauseAction getActionFrom(final WebSubTriggerSubscription subscription) {
            return new CauseAction(new WebSubCause(subscription));
        }
    }

    public void trigger(
            final WebSubTriggerSubscription subscription,
            final ImmutableMultimap<String, String> headers,
            final ImmutableMultimap<String, String> params,
            final String contents) {
        val cause = WebSubCause.getActionFrom(subscription);
        cast(job, ParameterizedJob.class)
                .ifPresent(j -> ParameterizedJobMixIn.scheduleBuild2(job, USE_DEFAULT_QUIET_PERIOD, cause));
    }

    // TODO: Is this really required? There's not an instance method for this for external access?
    public Job<?, ?> getJob() {
        return job;
    }

    // TODO: Add configuration fields
}
