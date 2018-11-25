package io.jenkins.plugins.websub.subscriber;

import com.googlecode.cqengine.ConcurrentIndexedCollection;
import com.googlecode.cqengine.IndexedCollection;
import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.index.navigable.NavigableIndex;
import com.googlecode.cqengine.index.unique.UniqueIndex;
import lombok.val;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.googlecode.cqengine.query.QueryFactory.*;

/**
 * Container for persisted subscriptions.
 */
public class WebSubSubscriptionRegistry {
    static private class SubscriptionAttributes {
        public static final Attribute<WebSubSubscription, String> ID =
                attribute("id", WebSubSubscription::getId);
        public static final Attribute<WebSubSubscription, Instant> EXPIRATION =
                attribute("expiration", WebSubSubscription::getExpiration);
    }
    private final IndexedCollection<WebSubSubscription> subscriptions = new ConcurrentIndexedCollection<>();

    public WebSubSubscriptionRegistry() {
        subscriptions.addIndex(UniqueIndex.onAttribute(SubscriptionAttributes.ID));
        subscriptions.addIndex(NavigableIndex.onAttribute(SubscriptionAttributes.EXPIRATION));
    }

    public Optional<WebSubSubscription> getById(final String id) {
        val query = equal(SubscriptionAttributes.ID, id);
        val results = subscriptions.retrieve(query);
        if (results.isEmpty())
            return Optional.empty();
        return Optional.of(results.iterator().next());
    }

    public List<WebSubSubscription> getExpiresBefore(Instant t) {
        val query = lessThan(SubscriptionAttributes.EXPIRATION, t);
        val results = subscriptions.retrieve(query);
        return results.stream().collect(Collectors.toList());
    }

    void add(WebSubSubscription subscription) {
        subscriptions.add(subscription);
    }

    boolean remove(String callbackId) {
        val subscription = getById(callbackId);
        return subscription.map(subscriptions::remove).orElse(false);
    }
}
