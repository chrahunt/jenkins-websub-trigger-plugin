# WebSub Trigger Plugin

[WebSub](https://www.w3.org/TR/websub/) (previously PubSubHubbub or PuSH) is a standard mechanism
for a publish-subscribe model in the open internet.

This plugin enables triggering jobs in real-time based on content published via WebSub. The plugin
handles negotiating and maintaining WebSub subscriptions to Topic URLs configured in individual
jobs. When a request comes in from a WebSub Hub, the appropriate job is located and triggered.

# WebSub vs WebHooks

1. WebSub is standardized - as more services and hub providers come online they will conform to
   the same standard interface, no need for provider-specific plugins or custom API calls.
2. WebSub subscriptions are self-maintaining. A WebSub subscription is issued for a configurable
   lease period and must be renewed otherwise the Hub will drop it. This model allows for less
   maintenance by WebSub clients without requiring complicated cleanups on the service provider,
   which in turn promotes more dynamic infrastructure.
3. A WebSub subscriber can only manage subscriptions for endpoints it has control over. As a result
   less permission is typically required for registering via WebSub (read access vs admin access),
   since there's no danger of removing other WebHooks.

# Project organization

- `src/main/java` - java sources
- `src/main/resources` - resources bundled into plugin
- `src/main/webapp` - icon
- `src/test` - tests

## Code organization

The plugin is organized into 2 main components:

1. `subscriber` - Jenkins-independent WebSub subscriber implementation.
- `io.jenkins.plugins.websub`
