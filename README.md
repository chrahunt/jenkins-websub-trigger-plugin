# WebSub Trigger Plugin

[WebSub](https://www.w3.org/TR/websub/) is a standard mechanism for a publish-subscribe model in the open internet.

This plugin implements the Subscriber interface required to allow a Job to subscribe to a Topic, exposed as a URL
that references a WebSub Hub. The plugin subscribes to the Hub, taking care of all negotiation. When a request comes in,
it is verified and then triggers the applicable job.

The benefit of WebSub over WebHooks is two-fold:

1. WebSub is standardized - as more services and hub providers come online they will all conform to the same standard
   interface, no need for proprietary plugins or custom API calls.
2. WebSub subscriptions are self-maintaining. A WebSub subscription is issued for a configurable lease period and then
   renewed prior to expiration (assuming Jenkins is still up). This model allows for ephemeral Jenkins without Hubs or
   Jenkins needing to worry about periodic cleanup tasks or "forgetting" about some configuration.

# Code organization
