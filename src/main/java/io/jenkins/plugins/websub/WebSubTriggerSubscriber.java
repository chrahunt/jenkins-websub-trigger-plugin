package io.jenkins.plugins.websub;

import io.jenkins.plugins.websub.subscriber.WebSubSubscriber;
import io.jenkins.plugins.websub.subscriber.WebSubSubscription;
import io.jenkins.plugins.websub.subscriber.WebSubSubscriptionRegistry;
import io.jenkins.plugins.websub.utils.JavaxServlet;
import jenkins.model.Jenkins;
import lombok.Value;
import lombok.val;
import org.kohsuke.stapler.StaplerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

import static jenkins.model.ParameterizedJobMixIn.ParameterizedJob;

class WebSubTriggerSubscriber extends WebSubSubscriber {
    private static final Logger logger = LoggerFactory.getLogger(WebSubTriggerSubscriber.class);

    WebSubTriggerSubscriber(final WebSubSubscriptionRegistry registry, final String baseUrl) {
        super(registry, baseUrl);
    }

    @Override
    protected void handleNotification(final WebSubSubscription subscription, final StaplerRequest request) {
        logger.info("Received notification for subscription {}", subscription.getId());
        val result = getJob(subscription.getId());

        if (!result.isPresent()) {
            logger.warn("No job found for subscription {}", subscription.getId());
            // TODO: Should we unsubscribe? No, because we don't know the corresponding topic URL.
            //  we may want to mark this as no good though,
            return;
        }

        val result2 = result.get();
        val headers = JavaxServlet.getRequestHeaders(request);
        val params = JavaxServlet.getRequestParams(request);
        String body;
        try {
            body = JavaxServlet.getRequestBody(request);
        } catch (IOException e) {
            logger.error(
                    "Error reading content from request {} (topic {}) for job {}: {}",
                    subscription.getId(), result2.getSubscription().getTopicUrl(), result2.getJob().getFullName(), e);
            return;
        }

        logger.info("Triggering {}", result2.job.getFullName());
        result2.getTrigger().trigger(
                result2.getSubscription(), headers, params, body);
    }

    @Value
    private static class SearchResult {
        ParameterizedJob job;
        WebSubTrigger trigger;
        WebSubTriggerSubscription subscription;
    }

    /**
     * Given a subscription, find the corresponding Jenkins job.
     * @param subscriptionId
     * @return
     */
    private Optional<SearchResult> getJob(final String subscriptionId) {
        Jenkins j = Jenkins.getInstance();
        for(val job : j.getAllItems(ParameterizedJob.class)) {
            val trigger = (WebSubTrigger) job.getTriggers().get(WebSubTrigger.DESCRIPTOR);
            if (trigger == null) continue;
            val sub = trigger.getSubscriptions().stream().filter(s -> subscriptionId.equals(s.getId())).findFirst();
            if (!sub.isPresent()) continue;
            return Optional.of(new SearchResult(job, trigger, sub.get()));
        }
        return Optional.empty();
    }

    @Override
    protected void handleSubscriptionSuccess(final WebSubSubscription subscription) {
        super.handleSubscriptionSuccess(subscription);
        // TODO: Optionally reflect status on job page.
    }

    @Override
    protected void handleSubscriptionRejection(final WebSubSubscription subscription) {
        // TODO: Reflect status on job page.
    }
}
