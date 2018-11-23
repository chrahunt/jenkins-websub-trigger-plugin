package io.jenkins.plugins.websub;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.Job;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

/**
 * A trigger associated with a Job that represents multiple subscriptions.
 */
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

    private List<WebSubTriggerSubscription> subscriptions = newArrayList();

    @DataBoundConstructor
    @SuppressWarnings("unused")
    public WebSubTrigger(final List<WebSubTriggerSubscription> subscriptions) {
        this.subscriptions = subscriptions;
    }

    public List<WebSubTriggerSubscription> getSubscriptions() {
        return subscriptions;
    }

    @Override
    public String toString() {
        return "WebSubTrigger [subscriptions="
                + subscriptions
                + "]";
    }

    // TODO: Add configuration fields (
    static {
        // Plugin initialization.
        //final WebSubSubscriptionRegistry registry =
        //        new WebSubJenkinsSubscriptionRegistry();
        //WebSubSubscriber.getInstance().setRegistry(registry);
        //WebSubSubscriberServer.getInstance().setRegistry(registry);
        // TODO: Set URL templates.
    }
}
