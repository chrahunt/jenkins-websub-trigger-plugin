package io.jenkins.plugins.websub.subscriber;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import lombok.val;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.WebApp;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static io.jenkins.plugins.websub.WebSubUtils.fmt;
import static io.jenkins.plugins.websub.WebSubUtils.getHttpResponseBody;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestWebSubSubscriber {
    private static Logger logger = LoggerFactory.getLogger(TestWebSubSubscriber.class);

    /**
     * Get a Client injected with a callback that takes request and response objects.
     * Handler should populate response, throwing if something went wrong.
     */
    @Deprecated
    WebSubSubscriber getClient(
            final String baseUrl,
            final BiConsumer<MockLowLevelHttpRequest, MockLowLevelHttpResponse> handler) {
        WebSubSubscriber client = new WebSubSubscriber(new WebSubSubscriptionRegistry(), baseUrl);
        client.setTransport(new MockHttpTransport() {
            @Override
            public LowLevelHttpRequest buildRequest(
                    final String method, final String url) {
                return new MockLowLevelHttpRequest(url) {
                    @Override
                    public LowLevelHttpResponse execute() {
                        MockLowLevelHttpResponse rsp =
                                new MockLowLevelHttpResponse();
                        handler.accept(this, rsp);
                        return rsp;
                    }
                };
            }
        });
        return client;
    }

    @Deprecated
    WebSubSubscriber getClient(
        final BiConsumer<MockLowLevelHttpRequest, MockLowLevelHttpResponse> handler) {
        return getClient("", handler);
    }

    private interface MockHttpHandler
            extends BiConsumer<MockLowLevelHttpRequest, MockLowLevelHttpResponse> {}

    // Returns true if request was accepted and filled.
    private interface ConditionalHttpHandler
            extends BiFunction<MockLowLevelHttpRequest, MockLowLevelHttpResponse, Boolean> {}

    private static class ClientBuilder {
        private final Map<String, MockHttpHandler> urlCallbacks = new HashMap<>();
        private final List<ConditionalHttpHandler> handlers = new ArrayList<>();
        private String baseUrl = "";

        ClientBuilder baseUrl(final String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        ClientBuilder handleUrl(final String url, final MockHttpHandler callback) {
            urlCallbacks.put(url, callback);
            return this;
        }

        ClientBuilder handleHttp(final ConditionalHttpHandler callback) {
            handlers.add(callback);
            return this;
        }

        WebSubSubscriber create() {
            final WebSubSubscriber client = new WebSubSubscriber(new WebSubSubscriptionRegistry(), this.baseUrl);
            client.setTransport(new MockHttpTransport() {
                @Override
                public LowLevelHttpRequest buildRequest(final String method, final String url) {
                    return new MockLowLevelHttpRequest(url) {
                        @Override
                        public LowLevelHttpResponse execute() {
                            val rsp = new MockLowLevelHttpResponse();
                            val callback = urlCallbacks.get(url);
                            if (callback != null) {
                                callback.accept(this, rsp);
                            } else {
                                handlers.stream()
                                        .filter(h -> h.apply(this, rsp))
                                        .findFirst()
                                        .orElseThrow(() -> new AssertionError(
                                                fmt("No method handler found for {}", url)));
                            }

                            return rsp;
                        }
                    };
                }
            });
            return client;
        }

        static WebSubSubscriber from(final MockHttpHandler callback) {
            return new ClientBuilder().handleHttp((req, rsp) -> {
                callback.accept(req, rsp);
                return true;
            }).create();
        }
    }

    @Test
    @DisplayName("100: HTTP Header Discovery")
    void testHTTPHeaderDiscovery() throws Exception {
        final String expectedHubUrl = "https://websub.rocks/blog/100/huburl";
        final String expectedTopicUrl = "https://websub.rocks/blog/100/topicurl";
        final String discoverTopicUrl = "https://websub.rocks/blog/100/topicUrlin";
        WebSubSubscriber client = ClientBuilder.from((req, rsp) -> {
            rsp.addHeader(
                    "Link",
                    fmt("<{}>; rel=\"hub\"", expectedHubUrl));
            rsp.addHeader(
                    "Link",
                    fmt("<{}>; rel=\"self\"", expectedTopicUrl));
            rsp.setStatusCode(200);
        });
        WebSubSubscriber.DiscoverResponse result = client.discover(discoverTopicUrl);
        assertEquals(expectedTopicUrl, result.topicUrl);
        assertEquals(1, result.hubUrls.size());
        assertEquals(expectedHubUrl, result.hubUrls.get(0));
    }

    @Test
    @DisplayName("100.1: HTTP Header Discovery (relative URI)")
    void testHTTPHeaderDiscoveryRelative() throws Exception {
        final String expectedHubUrl = "https://websub.rocks/blog/100/huburl";
        final String expectedTopicUrl = "https://websub.rocks/blog/100/topicurl";
        final String usedTopicUrl = "/blog/100/topicurl";
        final String discoverTopicUrl = "https://websub.rocks/blog/100/topicUrlin";
        WebSubSubscriber client = ClientBuilder.from((req, rsp) -> {
            rsp.addHeader(
                    "Link",
                    fmt("<{}>; rel=\"hub\"", expectedHubUrl));
            rsp.addHeader(
                    "Link",
                    fmt("<{}>; rel=\"self\"", usedTopicUrl));
            rsp.setStatusCode(200);
        });
        // Topic URL that returns response with
        // link: <Hub URL>; rel="hub"
        WebSubSubscriber.DiscoverResponse result = client.discover(discoverTopicUrl);
        assertEquals(expectedTopicUrl, result.topicUrl);
        assertEquals(1, result.hubUrls.size());
        assertEquals(expectedHubUrl, result.hubUrls.get(0));
    }

    @Test
    @DisplayName("101: HTML Tag Discovery")
    void testHTMLTagDiscovery() throws Exception {
        final String expectedHubUrl = "https://websub.rocks/blog/100/randomstring";
        final String expectedTopicUrl = "https://websub.rocks/blog/100/topicrandomstring";
        final String topicUrl = "https://websub.rocks/blog/100/topic";
        WebSubSubscriber client = ClientBuilder.from((req, rsp) ->
            rsp.setStatusCode(200)
               .setContentType("text/html")
               .setContent(String.join(
                "<html>",
                    "<head>",
                    fmt("<link rel=\"hub\" href=\"{}\">", expectedHubUrl),
                    fmt("<link rel=\"self\" href=\"{}\">", expectedTopicUrl),
                    "</head>",
                    "</html>")));
        WebSubSubscriber.DiscoverResponse result = client.discover(topicUrl);
        assertEquals(expectedTopicUrl, result.topicUrl);
        assertEquals(1, result.hubUrls.size());
        assertEquals(expectedHubUrl, result.hubUrls.get(0));
    }

    @Test
    @DisplayName("101.1: HTML Tag Discovery (relative)")
    void testHTMLTagDiscoveryRelative() throws Exception {
        final String expectedHubUrl = "https://websub.rocks/blog/100/randomstring";
        final String expectedTopicUrl = "https://websub.rocks/blog/100/topicrandomstring";
        final String usedTopicUrl = "/blog/100/topicrandomstring";
        final String topicUrl = "https://websub.rocks/blog/100/topic";
        WebSubSubscriber client = ClientBuilder.from((req, rsp) ->
            rsp.setStatusCode(200)
               .setContentType("text/html")
               .setContent(String.join(
                    "<html>",
                    "<head>",
                    fmt("<link rel=\"hub\" href=\"{}\">", expectedHubUrl),
                    fmt("<link rel=\"self\" href=\"{}\">", usedTopicUrl),
                    "</head>",
                    "</html>")));
        WebSubSubscriber.DiscoverResponse result = client.discover(topicUrl);
        assertEquals(expectedTopicUrl, result.topicUrl);
        assertEquals(1, result.hubUrls.size());
        assertEquals(expectedHubUrl, result.hubUrls.get(0));
    }

    @Disabled
    @Test
    @DisplayName("100.2: WebSubException on no content-type if no Link headers.")
    void testNoContentType() {
    }


    @Disabled
    @Test
    @DisplayName("102: Atom feed discovery")
    void testAtomFeedDiscovery() {
        // Topic URL that returns an Atom feed (shows as HTML in browser?)
    }

    @Disabled
    @Test
    @DisplayName("103: RSS feed discovery")
    void testRSSFeedDiscovery() {
        // Topic URL that returns an RSS feed (shows as HTML in browser?)
    }

    @Disabled
    @Test
    @DisplayName("104: Discovery priority")
    void testDiscoveryPriority() {
        // Topic URL that returns an RSS feed (shows as HTML in browser?)
    }

    // TODO: CommunicationException on non 2xx status code.

    /**
     * Attempt at stapler.test.JettyTestCase for jUnit 5.
     */
    /*
    class ServerExtension
            implements AfterTestExecutionCallback, BeforeTestExecutionCallback {
        private org.mortbay.jetty.Server server;
        private URL url;
        private Stapler stapler;
        private ServletContext servletContext;
        private WebApp webApp;

        @Override
        public void beforeTestExecution(ExtensionContext context)
                throws Exception {
            server = new org.mortbay.jetty.Server();
            server.setHandler(new WebAppContext("/noroot", ""));
            String contextPath = "";

            final Context ctx = new Context(server, contextPath, Context.SESSIONS);
            ctx.addServlet(new ServletHolder(new Stapler()), "/*");
            server.setHandler(ctx);

            SocketConnector connector = new SocketConnector();
            server.addConnector(connector);
            server.start();

            url = new URL(
                fmt(
                    "http://localhost:{}{}/callback",
                    connector.getLocalPort(), contextPath));
            servletContext = ctx.getServletContext();
            webApp = WebApp.get(servletContext);

            Optional<AnnotatedElement> element = context.getElement();
            if (!element.isPresent())
                return;
            AnnotatedElement element1 = element.get();
            ServerTest anno = element1.getAnnotation(ServerTest.class);
            Class<?> appClass = anno.value();
            webApp.setApp(appClass.getConstructor().newInstance());
        }

        @Override
        public void afterTestExecution(ExtensionContext context)
                throws Exception {
            server.stop();
        }
    }

    // I want to be able to call ExtendWith with a custom class dynamically
    // derived from my generic class above.
    @Target({ ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE })
    @Retention(RetentionPolicy.RUNTIME)
    @ExtendWith(ServerExtension.class)
    public @interface WithStaplerServer {
        @SuppressWarnings("unused")
        Class<?> value();
    }
    */

    public class ServerHandlerProvider {
        WebSubSubscriber subscriber;
        ServerHandlerProvider(WebSubSubscriber subscriber) {
            this.subscriber = subscriber;
        }

        // Used by Stapler.
        @SuppressWarnings("unused")
        public HttpResponse doCallback(final StaplerRequest request) {
            logger.debug("ServerHandlerProvider.doCallback()");

            return this.subscriber.handleRequest(request);
        }
    }

    /**
     * Brings up Jetty server to be used as endpoint for test cases.
     *
     * Later we may use a similar construction based on ngrok or another service for
     * testing against websub.rocks.
     */
    private class StaplerServer implements AutoCloseable {
        final private org.mortbay.jetty.Server server = new org.mortbay.jetty.Server();
        final private URL baseUrl;
        final private WebApp webApp;

        StaplerServer() throws Exception {
            server.setHandler(new WebAppContext("/noroot", ""));
            final Context ctx = new Context(server, "", Context.SESSIONS);
            ctx.addServlet(new ServletHolder(new Stapler()), "/*");
            server.setHandler(ctx);

            SocketConnector connector = new SocketConnector();
            server.addConnector(connector);
            server.start();

            baseUrl = new URL(
                    fmt("http://localhost:{}", connector.getLocalPort()));

            ServletContext servletContext = ctx.getServletContext();
            webApp = WebApp.get(servletContext);
        }

        void setCallback(Object callback) {
            webApp.setApp(callback);
        }

        URL getBaseUrl() { return baseUrl; }

        @Override
        public void close() {
            server.setGracefulShutdown(50);
        }
    }

    // This one is more of an integration test
    @Test
    @DisplayName("200: Subscribing to a URL that reports a different rel=self")
    void testSubscribeAcceptsWithDifferentRelSelf() throws Exception {
        val hubUrl = "http://example.com/hub";
        val topicUrlAttempted = "http://example.com/resource";
        val topicUrlReturned = "http://example.com/actual-resource";
        val hubChallenge = "hello world";
        com.google.api.client.http.HttpResponse response;
        // Server test auto-closes server at end of block.
        try (val server = new StaplerServer()) {
            // This URL format is due to the doCallback method in ServerHandlerProvider.
            val baseUrl = fmt("{}/callback", server.getBaseUrl());
            // TODO: Default boxed value? Can it be null?
            val gotTopicRequest = new ClosureVal<Boolean>(false);
            val gotHubRequest = new ClosureVal<Boolean>(false);
            val callbackUrl = new ClosureVal<String>();
            final WebSubSubscriber client = new ClientBuilder()
                .baseUrl(baseUrl)
                // Discovery requests.
                .handleUrl(topicUrlAttempted, (req, rsp) -> {
                    gotTopicRequest.value = true;
                    rsp.addHeader(
                            "Link",
                            fmt("<{}>; rel=\"hub\"", hubUrl));
                    rsp.addHeader(
                            "Link",
                            fmt("<{}>; rel=\"self\"", topicUrlReturned));
                    rsp.setStatusCode(200);
                })
                .handleUrl(hubUrl, (req, rsp) -> {
                    // Hub subscription request.
                    try {
                        // TODO: Actually extract and use the applicable callback URL.
                        callbackUrl.value = req.getContentAsString();
                        gotHubRequest.value = true;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    rsp.setStatusCode(202);
                })
                .create();
            server.setCallback(new ServerHandlerProvider(client));

            // Actually do discovery.
            final WebSubSubscriber.DiscoverResponse result = client.discover(topicUrlAttempted);
            assertTrue(gotTopicRequest.value);
            assertEquals(topicUrlReturned, result.topicUrl);
            assertEquals(1, result.hubUrls.size());
            assertEquals(hubUrl, result.hubUrls.get(0));

            // Subscription will generate an id we use in verification of intent.
            String subscriptionId = client.subscribe(result.hubUrls.get(0), result.topicUrl);
            assertTrue(gotHubRequest.value);

            // Construct and send verification of intent request.
            final HttpTransport transport = new NetHttpTransport();
            final HttpRequestFactory requestFactory = transport.createRequestFactory();
            GenericUrl url = new GenericUrl(fmt("{}/{}", baseUrl, subscriptionId));
            url.put("hub.mode", "subscribe");
            url.put("hub.topic", result.topicUrl);
            url.put("hub.challenge", hubChallenge);
            url.put("hub.lease_seconds", "45");
            final HttpRequest request = requestFactory.buildGetRequest(url);
            request.setThrowExceptionOnExecuteError(false);
            response = request.execute();
        }

        assertEquals(200, response.getStatusCode(), fmt("Unexpected response {}", response));
        // Decode and verify challenge response.
        String responseContent = getHttpResponseBody(response);
        assertEquals(hubChallenge, responseContent);
    }

    // 200.1: Subscribing to a URL that sends denied immediately.
    // 200.2: Subscribing to a URL that sends denied after previously sending

    // 201: Subscribing to a topic URL that sends an HTTP 302 temporary redirect
    // 202: Subscribing to a topic URL that sends an HTTP 301 permanent redirect
    // 203: Subscribing to a hub that sends a 302 temporary redirect
    // 204: Subscribing to a hub that sends a 301 permanent redirect

    @Test
    @DisplayName("205: Rejects a verification request with an invalid topic URL")
    void testSubscribeRejectsInvalidTopicUrl() throws Exception {
        val hubUrl = "http://example.com/hub";
        val topicUrl = "http://example.com/feed";
        com.google.api.client.http.HttpResponse response;
        try (val server = new StaplerServer()) {
            val baseUrl = fmt("{}/callback", server.getBaseUrl());
            final WebSubSubscriber client = new ClientBuilder()
                    .baseUrl(baseUrl)
                    .handleUrl(topicUrl, (req, rsp) -> {
                        rsp.addHeader(
                                "Link",
                                fmt("<{}>; rel=\"hub\"", hubUrl));
                        rsp.addHeader(
                                "Link",
                                fmt("<{}>; rel=\"self\"", topicUrl));
                        rsp.setStatusCode(200);
                    })
                    .handleUrl(hubUrl, (req, rsp) -> rsp.setStatusCode(202))
                    .create();
            server.setCallback(new ServerHandlerProvider(client));
            String id = client.subscribe(hubUrl, topicUrl);
            final HttpTransport transport = new NetHttpTransport();
            final HttpRequestFactory requestFactory = transport.createRequestFactory();
            GenericUrl url = new GenericUrl(fmt("{}/{}", baseUrl, id));
            url.put("hub.mode", "subscribe");
            url.put("hub.topic", topicUrl + "junk");
            url.put("hub.challenge", "hello world");
            url.put("hub.lease_seconds", "3600");
            final HttpRequest request = requestFactory.buildGetRequest(url);
            request.setThrowExceptionOnExecuteError(false);
            response = request.execute();
        }

        assertEquals(404, response.getStatusCode());
    }

    class ClosureVal<T> {
        T value;
        ClosureVal() {}
        ClosureVal(T value) { this.value = value; }
    }

    // 205.1: Rejects an unknown subscription request with 404.
    @Test
    void testSubscribeRejectsOnUnknownSubscription() {

    }

    // 205.2: Rejects a mismatched subscription action (unsubscribe when
    // subscribed) with 404.
    @Test
    void testSubscribeRejectsOnWrongAction() {

    }

    // 300: Returns HTTP 2xx when the notification payload is delivered
    // 301: Rejects a distribution request with an invalid signature
    // 302: Rejects a distribution request with no signature when the
    // subscription was made with a secret
}
