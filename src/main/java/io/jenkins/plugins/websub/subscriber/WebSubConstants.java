package io.jenkins.plugins.websub.subscriber;

final class WebSubConstants {
    private WebSubConstants() {}

    static class Modes {
        static final String SUBSCRIBE = "subscribe";
        static final String UNSUBSCRIBE = "unsubscribe";
        static final String DENIED = "denied";
    }

    static class HubParams {
        static final String CALLBACK = "hub.callback";
        static final String CHALLENGE = "hub.challenge";
        static final String LEASE_SECONDS = "hub.lease_seconds";
        static final String MODE = "hub.mode";
        static final String REASON = "hub.reason";
        static final String SECRET = "hub.secret";
        static final String TOPIC = "hub.topic";
    }
}
