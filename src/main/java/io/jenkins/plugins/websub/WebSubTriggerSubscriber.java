package io.jenkins.plugins.websub;

import com.google.api.client.http.UrlEncodedParser;
import com.google.common.collect.ImmutableMultimap;
import io.jenkins.plugins.websub.subscriber.WebSubSubscriber;
import io.jenkins.plugins.websub.subscriber.WebSubSubscription;
import io.jenkins.plugins.websub.subscriber.WebSubSubscriptionRegistry;
import jenkins.model.Jenkins;
import lombok.Value;
import lombok.val;
import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static jenkins.model.ParameterizedJobMixIn.ParameterizedJob;
import static io.jenkins.plugins.websub.WebSubUtils.toStream;

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

        try {
            logger.info("Triggering {}", result2.job.getFullName());
            result2.getTrigger().trigger(
                    result2.getSubscription(), getHeaders(request), getParams(request), getBody(request));
        } catch (IOException e) {
            logger.error(
                    "Error reading content from request {} (topic {}) for job {}: {}",
                    subscription.getId(), result2.getSubscription().getTopicUrl(), result2.getJob().getFullName(), e);
        }
    }

    @Value
    private static class SearchResult {
        ParameterizedJob job;
        WebSubTrigger trigger;
        WebSubTriggerSubscription subscription;
    }

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

    private ImmutableMultimap<String, String> getHeaders(final HttpServletRequest request) {
        final ImmutableMultimap.Builder<String, String> builder = ImmutableMultimap.builder();
        toStream(request.getHeaderNames())
                .forEach(k -> builder.putAll(k, Collections.list(request.getHeaders(k))));
        return builder.build();
    }

    private ImmutableMultimap<String, String> getParams(final HttpServletRequest request) {
        ImmutableMultimap.Builder<String, String> builder = ImmutableMultimap.builder();
        Map<String, List<String>> data = new HashMap<>();
        UrlEncodedParser.parse(request.getQueryString(), data);
        data.forEach(builder::putAll);
        return builder.build();
    }

    private String getBody(final HttpServletRequest request) throws IOException {
        // TODO: Enforce limits on payload size.
        BufferedReader reader = request.getReader();
        return IOUtils.toString(reader);
    }

    @Override
    protected void handleSubscriptionSuccess(final WebSubSubscription subscription) {
        super.handleSubscriptionSuccess(subscription);
        // Optionally reflect status on job page.
    }

    @Override
    protected void handleSubscriptionRejection(final WebSubSubscription subscription) {

    }
}
