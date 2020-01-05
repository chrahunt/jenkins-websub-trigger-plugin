package io.jenkins.plugins.websub;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.listeners.ItemListener;
import hudson.triggers.SafeTimerTask;
import hudson.util.DaemonThreadFactory;
import hudson.util.NamingThreadFactory;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.val;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.jenkins.plugins.websub.utils.Generic.cast;
import static jenkins.model.ParameterizedJobMixIn.ParameterizedJob;
import static io.jenkins.plugins.websub.subscriber.WebSubSubscriber.DiscoverResponse;

/**
 * Listen for changes to jobs and sends out subscribe/unsubscribe events.
 */
@Extension
@SuppressWarnings("unused") // Used by Jenkins.
public class WebSubItemListener extends ItemListener {
    private final static Logger logger = LoggerFactory.getLogger(WebSubItemListener.class);
    private static ExecutorService executorService;

    @Override
    public void onDeleted(final Item item) {
        logger.info("onDeleted()");
        // TODO: Unsubscribe from the job.
    }

    @Override
    public void onLocationChanged(final Item item, final String oldFullName, final String newFullName) {
        logger.info("onLocationChanged()");
        // TODO: Update SharedResources.jobMap to point to the new full name.
    }

    @Override
    public void onCopied(final Item src, final Item item) {
        logger.info("onCopied()");
        // TODO: If it has a Trigger added then attempt to subscribe for the new item.
    }

    @Override
    public void onUpdated(final Item item) {
        logger.info("onUpdated()");
        // Create any new subscriptions or unsubscribe from old ones.
        // Also update jobMap to remove the triggering.
        // Will we still have access to the previous configuration?
        // TODO: Get access to the previous configuration so we can unsubscribe.
        getTrigger(item).ifPresent(this::doOperations);
    }

    @Override
    public void onCreated(final Item item) {
        logger.info("onCreated()");
    }

    private Optional<WebSubTrigger> getTrigger(final Item item) {
        return cast(item, ParameterizedJob.class)
                .map(j -> j.getTriggers().get(WebSubTrigger.DESCRIPTOR))
                .map(WebSubTrigger.class::cast);
    }

    private void doOperations(final WebSubTrigger trigger) {
        getExecutorService().submit(new SafeTimerTask() {
            @Override
            protected void doRun() throws Exception {
                val job = trigger.getJob();
                logger.info("Doing update for job {}", job.getFullName());
                val subs = trigger.getSubscriptions();
                for (val sub : subs) {
                    if (sub.getId() != null) {
                        // TODO: Allow forced re-subscription.
                        logger.debug("Already subscribed to {}, skipping.", sub.getTopicUrl());
                        continue;
                    }

                    val client = WebSubSharedResources.getInstance().getClient();
                    logger.info("Discovering {}", sub.getTopicUrl());
                    DiscoverResponse response = client.discover(sub.getTopicUrl());
                    if (response.hubUrls.size() == 0) {
                        logger.warn("Could not get hub URLs for {}.", sub.getTopicUrl());
                    } else if (response.topicUrl == null) {
                        logger.warn("Could not get topic URL for {}.", sub.getTopicUrl());
                    } else {
                        String id = client.subscribe(response.hubUrls.get(0), response.topicUrl);
                        val jobMap = WebSubSharedResources.getInstance().getJobMap();
                        logger.info("Saving id {} for job {}", id, trigger.getJob().getFullName());
                        // Why is this commented?
                        //jobMap.put(id, trigger.getJob().getFullName());
                        sub.setId(id);
                    }
                }
            }
        });
    }

    private static synchronized ExecutorService getExecutorService() {
        if (executorService == null) {
            executorService = Executors.newSingleThreadExecutor(new NamingThreadFactory(new DaemonThreadFactory(), WebSubItemListener.class.getName()));
        }
        return executorService;
    }
}
