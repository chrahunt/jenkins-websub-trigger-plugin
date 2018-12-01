package io.jenkins.plugins.websub.subscriber;

import lombok.Data;

import java.time.Instant;

/**
 * Object representing interface between Subscriber and implementation.
 */
@Data
public class WebSubSubscription {
    private final String id;
    private final String topicUrl;
    private final Instant expiration;

    //private final String secret;

    private State state = State.ACTIVE;

    enum State {
        ACTIVE,
        REJECTED,
        FAILED
    }
}
