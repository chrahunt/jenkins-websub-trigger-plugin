package io.jenkins.plugins.websub.subscriber;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpMediaType;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.UrlEncodedContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import io.jenkins.plugins.websub.subscriber.WebSubConstants.HubParams;
import io.jenkins.plugins.websub.subscriber.WebSubConstants.Modes;
import io.jenkins.plugins.websub.utils.GoogleApiClient;
import lombok.Data;
import lombok.NonNull;
import lombok.val;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Link;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.mapping;

import static io.jenkins.plugins.websub.utils.Generic.fmt;

/**
 * WebSub subscriber implementation.
 *
 * We use com.google.api.client.http.HttpTransport for HTTP client
 * communication.
 *
 * For server-side communication we assume a subscriber instance will be added
 * as a handler to a server providing filtered requests to the client.
 *
 * The Subscriber keeps two data stores:
 *
 * 1. persistent storage - for successful subscriptions expecting ongoing
 *    notifications.
 * 2. internal storage - for persistence between subscribe and callback invocation
 *    but not meant to be persisted across application restart.
 *
 * Not thread-safe.
 *
 * TODO: Gracefully handle base url update by doing re-subscription when requests come in.
 *
 * For Subscriber-side reconciliation, the callbackUrl must have a unique identifier
 * as part of its path.
 *
 * References:
 * * WebSub: https://www.w3.org/TR/websub/
 * * Link header fields: https://tools.ietf.org/html/rfc5988
 * * IRI -> URI: https://tools.ietf.org/html/rfc3987#section-3.1
 * * https://www.baeldung.com/google-http-client
 */
public class WebSubSubscriber {
    private static final Logger logger = LoggerFactory.getLogger(WebSubSubscriber.class);

    public HttpTransport transport = new NetHttpTransport();

    /**
     * HTTP-related exception.
     */
    public static class CommunicationException extends Exception {
        CommunicationException(String msg) {
            super(msg);
        }
    }

    /**
     * Exception in WebSub protocol.
     */
    public static class WebSubException extends Exception {
        WebSubException(String msg) {
            super(msg);
        }
    }

    /**
     * Represents an ongoing subscription operation.
     */
    static class PendingSubscription {
        String mode;
        String hubUrl;
        String topicUrl;
        String callbackId;
        // TODO: secret
        int leaseSeconds;
        // TODO: Sent-time, so they can be cleaned up if not responded in enough time.
        WebSubSubscription toSubscription(final Instant expiration) {
            return new WebSubSubscription(callbackId, topicUrl, expiration);
        }
    }

    @Data
    public static class Options {
        /**
         * Base URL to use for callbacks.
         */
        @NonNull final String baseUrl;
        /**
         * Default lease seconds to request, or 0 to not include in subscription
         * requests.
         */
        int leaseSeconds = 0;

        // Base retry interval for failed subscriptions.
        Duration baseRetryInterval = Duration.ofMinutes(5);

        // TODO: Limits for Callback URL length, parameter size.
    }

    private final WebSubSubscriptionRegistry registry;
    private final Options options;

    // callback id mapped to pending subscriptions
    private Map<String, PendingSubscription> pendingSubscriptions = new HashMap<>();

    /**
     * @param registry the container for subscriptions
     * @param baseUrl the URL used as the base for constructing callback URLs. Callback URLs will
     *                have format: {baseUrl}/{callbackId}.
     */
    public WebSubSubscriber(final WebSubSubscriptionRegistry registry, final String baseUrl) {
        this(registry, new Options(baseUrl));
    }

    public WebSubSubscriber(final WebSubSubscriptionRegistry registry, final Options options) {
        this.registry = registry;
        this.options = options;
    }

    /**
     * Override to set headers, client certs, http timeout, etc on outgoing
     * requests.
     * TODO: Determine if this interface is enough for dictating retries or
     *  if we need to expose something more.
     * @param transport
     */
    public void setTransport(final HttpTransport transport) {
        this.transport = transport;
    }

    // TODO: Global options, for HTTP timeout and lease_seconds.

    /**
     * Subscribe to given hub/topic with callback. Call is executed synchronously.
     * Any error in response is given
     * TODO: Take secret or auto-generate.
     * TODO: Try HTTPS hub URL first
     * TODO: Easier URL param support.
     * @param hubUrl as retrieved from the call to discover.
     * @param topicUrl as retrieved from the call to discover.
     * @param callbackId unique identifier for this subscription. If null then
     *                   one will be generated and returned.
     * @throws CommunicationException on any HTTP error.
     */
    public void subscribe(final String hubUrl, final String topicUrl, final String callbackId)
                throws IOException, WebSubException, CommunicationException {
        val subscription = new PendingSubscription();
        subscription.mode = Modes.SUBSCRIBE;
        subscription.callbackId = callbackId;
        subscription.hubUrl = hubUrl;
        subscription.topicUrl = topicUrl;
        subscription.leaseSeconds = options.leaseSeconds;
        sendImpl(subscription.hubUrl, subscription, new ArrayList<>());
    }

    /**
     * No parameters, autogenerated id.
     *
     * @param hubUrl http endpoint against which to subscribe
     * @param topicUrl topic to include in subscription request
     * @return the generated id for the callback
     * @throws IOException
     * @throws WebSubException
     * @throws CommunicationException
     */
    public String subscribe(final String hubUrl, final String topicUrl)
            throws IOException, WebSubException, CommunicationException {
        final String callbackId = UUID.randomUUID().toString();
        subscribe(hubUrl, topicUrl, callbackId);
        return callbackId;
    }

    public void unsubscribe(final String hubUrl, final String topicUrl, final String callbackId) {
    }

    /**
     * Inner method, separate for redirect loop detection.
     *
     * @param hubUrl the hub URL to use for the request
     * @param subscription object with subscription details
     * @param urls URLs redirected through so far
     * @throws IOException
     * @throws WebSubException
     * @throws CommunicationException
     */
    private void sendImpl(
            final String hubUrl, final PendingSubscription subscription,
            final List<String> urls)
                throws IOException, WebSubException, CommunicationException {
        // Create request content.
        final Map<String, String> data = new HashMap<>();
        data.put(HubParams.MODE, subscription.mode);
        data.put(HubParams.TOPIC, subscription.topicUrl);
        data.put(HubParams.CALLBACK, options.baseUrl + "/" + subscription.callbackId);
        if (subscription.leaseSeconds != 0)
            data.put(HubParams.LEASE_SECONDS, Integer.toString(subscription.leaseSeconds));
        val body = new UrlEncodedContent(data);

        // Create request.
        final HttpRequestFactory requestFactory =
                transport.createRequestFactory();
        final HttpRequest request =
                requestFactory.buildPostRequest(new GenericUrl(hubUrl), body);
        request.setUnsuccessfulResponseHandler(
                (HttpRequest request1, HttpResponse response, boolean retrySupported) -> false);
        request.setIOExceptionHandler(
                (HttpRequest request1, boolean retrySupported) -> false);

        // Send request.
        // TODO: Handle IO Exception.
        final HttpResponse response = request.execute();
        if (response.getStatusCode() == 202) {
            pendingSubscriptions.put(subscription.callbackId, subscription);
        } else {
            final int statusCode = response.getStatusCode();
            // TODO: Handle additional redirect types.
            if (statusCode == 307 || statusCode == 308) {
                String location = response.getHeaders().getLocation();
                if (location == null)
                    throw new WebSubException("Hub redirect did not have Location header");
                if (urls.contains(location))
                    throw new CommunicationException("Detected redirect loop");
                urls.add(location);
                sendImpl(location, subscription, urls);
            } else {
                throw new CommunicationException(
                    fmt("Received status code {}", statusCode));
            }
        }
    }

    /**
     * Handle incoming HTTP request as server. Should only be called with
     * applicable requests, as validation assumes that the request is meant to
     * be a subscription response or notification.
     *
     * A user providing requests may implement pre-validation based on e.g.
     * URL prefix.
     */
    public org.kohsuke.stapler.HttpResponse handleRequest(final StaplerRequest request) {
        logger.debug("Received request {}", request);
        val req = new IncomingRequest(request);
        String method = request.getMethod();
        if (method.equals("POST"))
            return handlePostRequest(req);
        else if (method.equals("GET"))
            return handleGetRequest(req);
        else
            return HttpResponses.error(
                    HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                    "Only GET/POST supported.");
    }

    /**
     * GET requests are for subscription management.
     *
     * @param request incoming HTTP request
     * @return fully-formed response
     * @throws HttpResponses.HttpResponseException for most HTTP error cases.
     */
    private org.kohsuke.stapler.HttpResponse handleGetRequest(final IncomingRequest request)
            throws HttpResponses.HttpResponseException {
        val mode = request.getParam(HubParams.MODE)
                .orElseThrow(() -> HttpResponses.error(
                        400, fmt("Request must have one '{}' parameter.", HubParams.MODE)));

        if (mode.equals(Modes.DENIED))
            return handleDeniedRequest(request);

        if (mode.equals(Modes.SUBSCRIBE) || mode.equals(Modes.UNSUBSCRIBE))
            return handleSubscribeUnsubscribeRequest(mode, request);

        return HttpResponses.error(
                400, fmt("'{}' value must be one of denied, subscribe, or unsubscribe.", HubParams.MODE));
    }

    private org.kohsuke.stapler.HttpResponse handleSubscribeUnsubscribeRequest(
            final String mode, final IncomingRequest request) {
        PendingSubscription sub = request.getPendingSubscription()
                .orElseThrow(() -> HttpResponses.error(404, "No subscription found."));

        val topic = request.getParam(HubParams.TOPIC)
                .orElseThrow(() -> HttpResponses.error(
                        400, "Request must have one 'hub.topic' parameter."));

        if (!topic.equals(sub.topicUrl))
            return HttpResponses.error(404, "Topic URL does not match expected.");

        val challenge = request.getParam(HubParams.CHALLENGE)
                .orElseThrow(() -> HttpResponses.error(
                        400, "Request must have one 'hub.challenge' parameter."));

        Duration duration;
        if (mode.equals(Modes.SUBSCRIBE)) {
            duration = request.getParam(HubParams.LEASE_SECONDS)
                    .map(Integer::parseInt)
                    .map(Duration::ofSeconds)
                    .orElseThrow(() -> HttpResponses.error(
                            400, "Subscription requests must have one 'hub.lease_seconds' parameter."));
        } else {
            duration = options.baseRetryInterval;
        }
        Instant expiration = Instant.now().plus(duration);
        WebSubSubscription subscription = sub.toSubscription(expiration);

        if (mode.equals(Modes.SUBSCRIBE)) {
            registry.add(subscription);
            handleSubscriptionSuccess(subscription);
        } else {
            registry.remove(subscription.getId());
            handleUnsubscriptionSuccess(subscription);
        }
        // XXX: Use HttpResponses.text when Jenkins moves to Stapler 1.255.
        // XXX: We do not handle failure to write response.
        return (req, rsp, node) -> {
            rsp.setContentType("text/plain;charset=UTF-8");
            PrintWriter pw = rsp.getWriter();
            pw.print(challenge);
            pw.flush();
        };
    }

    private org.kohsuke.stapler.HttpResponse handleDeniedRequest(final IncomingRequest request)
            throws HttpResponses.HttpResponseException {
        // We can receive a denial at any time, for pending subscriptions or new ones.
        val sub = request.getSubscription()
                .orElseGet(() ->
                        request.getPendingSubscription()
                                .map(s -> s.toSubscription(Instant.now().plus(options.baseRetryInterval)))
                                .orElseThrow(() ->
                                        HttpResponses.error(404, "No subscription found.")));
        val topic = request.getParam(HubParams.TOPIC)
                .orElseThrow(() ->
                        HttpResponses.error(400, "Request must have one 'hub.topic' parameter."));
        if (!topic.equals(sub.getTopicUrl()))
            return HttpResponses.error(404, "Topic URL does not match expected.");
        // If the denial was for a pending subscription then we should only remove it from the pending
        // subscription container after we've verified the topic URL.
        pendingSubscriptions.remove(sub.getId());
        sub.setState(WebSubSubscription.State.REJECTED);
        // Overwriting is OK.
        registry.add(sub);
        handleSubscriptionRejection(sub);
        return HttpResponses.ok();
    }

    private class IncomingRequest {
        final StaplerRequest request;
        final String id;

        IncomingRequest(final StaplerRequest request) {
            this.request = request;
            int i = request.getRequestURL().lastIndexOf("/") + 1;
            id = request.getRequestURL().substring(i);
        }

        Optional<PendingSubscription> getPendingSubscription() {
            return Optional.ofNullable(pendingSubscriptions.get(id));
        }

        Optional<WebSubSubscription> getSubscription() {
            return registry.getById(id);
        }

        Optional<String> getParam(final String name) {
            val params = request.getParameterValues(name);
            if (params.length != 1)
                return Optional.empty();
            return Optional.of(params[0]);
        }
    }

    /**
     * POST requests contain actual content to be propagated to listener, if it matches an
     * active subscription.
     */
    private org.kohsuke.stapler.HttpResponse handlePostRequest(final IncomingRequest request) {
        val subscription = request.getSubscription()
                .orElseThrow(() -> HttpResponses.error(404, "No subscription found."));
        handleNotification(subscription, request.request);
        return HttpResponses.ok();
    }

    public static class DiscoverResponse {
        public String topicUrl;
        public List<String> hubUrls = new ArrayList<>();
    }

    /**
     * @param topicUrl the url to query
     * @return the priority ordered list of hub URLs and
     * @throws IOException from buildHeadRequest.
     * @throws CommunicationException if any HTTP error is received.
     * @throws WebSubException if any WebSub protocol error occurs.
     */
    public DiscoverResponse discover(String topicUrl)
            throws IOException, CommunicationException, WebSubException {
        // TODO: Trace warning if permanent redirect is encountered, so it can be
        //  acted on.
        HttpRequestFactory requestFactory =
                transport.createRequestFactory();
        // Issue HEAD request to check for Link headers.
        HttpRequest request =
                requestFactory.buildHeadRequest(new GenericUrl(topicUrl));
        HttpResponse response = request.execute();
        // TODO: If request error (non 2xx), then raise a CommunicationException.
        DiscoverResponse result = checkHeaders(response);
        if (result.topicUrl != null && result.hubUrls.size() > 0)
            return result;

        // If insufficient Link headers found, issue GET request for resource.
        response = request.setRequestMethod("GET").execute();
        // In case of some race condition, prefer the latter-retrieved headers.
        DiscoverResponse result2 = checkHeaders(response);
        if (result2.topicUrl != null)
            result.topicUrl = result2.topicUrl;
        if (result2.hubUrls.size() > 0)
            result.hubUrls = result2.hubUrls;

        // If we actually found all links then return.
        boolean needTopicUrl = result.topicUrl == null;
        boolean needHubUrls = result.hubUrls.size() == 0;
        if (!needTopicUrl && !needHubUrls)
            return result;

        HttpMediaType mediaType = response.getMediaType();
        if (mediaType == null)
            throw new WebSubException("No content-type provided with response.");

        // If result is HTML, check head for 'link' elements.
        if ("text".equals(mediaType.getType()) && "html".equals(mediaType.getSubType())) {
            // TODO: Use incremental parsing and provide configuration to limit max size.
            String contents = GoogleApiClient.getHttpResponseBody(response);
            // TODO: Jsoup.parse overload that takes encoding?
            Document doc = Jsoup.parse(contents, topicUrl);
            Elements links = doc.select("html > head > link");
            for (Element link : links) {
                Attributes attrs = link.attributes();
                if (!attrs.hasKey("href")) continue;
                if (!attrs.hasKey("rel")) continue;
                String rel = attrs.get("rel");
                if (needTopicUrl && rel.equals("self")) {
                    result.topicUrl = link.absUrl("href");
                    needTopicUrl = false;
                } else if (needHubUrls && rel.equals("hub")) {
                    result.hubUrls.add(link.absUrl("href"));
                }
            }
            return result;
        } else {
            throw new WebSubException("Response not understood as WebSub contents.");
        }
        // TODO: XML parsing
    }

    private DiscoverResponse checkHeaders(HttpResponse response) {
        URI base = response.getRequest().getUrl().toURI();
        HttpHeaders responseHeaders = response.getHeaders();
        List<String> linkHeaders =
                responseHeaders.getHeaderStringValues("Link");
        @Data
        class TaggedURI {
            final String rel;
            final String url;
        }
        Map<String, List<String>> links = linkHeaders.stream()
            .map(Link::valueOf)
            .map(l -> new TaggedURI(l.getRel(), base.resolve(l.getUri()).toString()))
            .collect(
                groupingBy(TaggedURI::getRel, mapping(TaggedURI::getUrl, toList())));
        List<String> selfs = links.get("self");
        List<String> hubs = links.get("hub");
        val result = new DiscoverResponse();
        if (selfs != null)
            result.topicUrl = selfs.get(0);
        if (hubs != null)
            result.hubUrls = hubs;
        return result;
    }

    /**
     * Invoked on successful subscription.
     * Subscription has already been added to the registry.
     * @param subscription the new subscription
     */
    protected void handleSubscriptionSuccess(final WebSubSubscription subscription) {
        logger.info("Subscribe succeeded for {}", subscription.getId());
    }

    /**
     * Invoked on explicit rejection of a subscription by the hub.
     * @param subscription the rejected subscription
     */
    protected void handleSubscriptionRejection(final WebSubSubscription subscription) {
        logger.warn("Subscribe rejected for {}", subscription.getId());
        registry.remove(subscription.getId());
    }

    /**
     * XXX: Not implemented.
     * Invoked on subscription request timeout.
     * @param subscription the failed subscription
     */
    protected void handleSubscriptionFailed(final WebSubSubscription subscription) {
        logger.warn("Subscribe failed for {}", subscription.getId());
        registry.remove(subscription.getId());
    }

    /**
     * Invoked on unsubscribe success.
     * Subscription has already been removed from the registry.
     */
    protected void handleUnsubscriptionSuccess(final WebSubSubscription subscription) {
        logger.info("Unsubscribe succeeded for {}", subscription.getId());
    }

    /**
     * XXX: Not implemented.
     * Invoked on unsubscribe request timeout.
     * @param subscription the failed un-subscription.
     */
    protected void handleUnsubscriptionFailed(final WebSubSubscription subscription) {
        logger.warn("Unsubscribe failed for {}", subscription.getId());
        registry.remove(subscription.getId());
    }

    /**
     * Invoked when a message has been received from the hub for a particular message.
     * XXX: Once implemented, this will only be invoked AFTER secret validation.
     * Subclasses may implement restrictions on data read, etc.
     * @param subscription the subscription corresponding to the notification
     * @param request the request itself which can be read for contents
     */
    protected void handleNotification(final WebSubSubscription subscription, final StaplerRequest request) {
        logger.debug("Received notification for {}. Size: {}", subscription.getId(), request.getContentLength());
    }

    /**
     * XXX: Not implemented.
     * Invoked on automatic refresh of the subscription.
     * @param id the id of the old subscription (which has already been removed).
     * @param subscription the new subscription.
     */
    protected void handleSubscriptionRefresh(final String id, final WebSubSubscription subscription) {
        logger.debug("Subscription {} has been refreshed and is now {}", id, subscription.getId());
    }
}
