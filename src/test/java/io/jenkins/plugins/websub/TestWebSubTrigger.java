package io.jenkins.plugins.websub;

import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.tasks.Shell;
import io.jenkins.plugins.websub.test.StaplerServer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import jenkins.model.JenkinsLocationConfiguration;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import lombok.val;
import org.apache.commons.io.FileUtils;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.jenkins.plugins.websub.test.JenkinsRule;
import io.jenkins.plugins.websub.WebSubUtils.ClosureVal;
import io.jenkins.plugins.websub.WebSubUtils.LockGuard;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.jenkins.plugins.websub.WebSubUtils.fmt;
import static org.awaitility.Awaitility.await;

@ExtendWith(JenkinsRule.Resolver.class)
@ExtendWith(StaplerServer.Resolver.class)
public class TestWebSubTrigger {
    private static final Logger logger = LoggerFactory.getLogger(TestWebSubTrigger.class);
    @Test
    void testJenkinsParameterResolver(JenkinsRule j) throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(new Shell("echo hello"));
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        System.out.println(build.getDisplayName() + " completed");
        String s = FileUtils.readFileToString(build.getLogFile());
        System.out.println(s);
    }

    /**
     * Example hub for testing.
     */
    public static class Hub {
        private static final Logger logger = LoggerFactory.getLogger(Hub.class);

        private final Multimap<String, String> subscribers = ArrayListMultimap.create();
        private final Consumer<SubscribeRequest> subscribeCallback;
        private HttpTransport transport = new NetHttpTransport();
        @Getter @Setter String hubUrl;
        @Getter @Setter String topicUrl;

        // Used by Stapler.
        @SuppressWarnings("unused")
        public HttpResponse doGettopic(final StaplerRequest request) {
            logger.info("Received topic request");
            return HttpResponses.html(String.join(
                    "<html>",
                    "<head>",
                    fmt("<link rel=\"hub\" href=\"{}\">", hubUrl),
                    fmt("<link rel=\"self\" href=\"{}\">", topicUrl),
                    "</head>",
                    "</html>"));
        }

        @Value
        static class SubscribeRequest {
            String mode;
            String topicUrl;
            String callbackUrl;
        }

        // Used by Stapler.
        @SuppressWarnings("unused")
        public HttpResponse doSubscribe(final StaplerRequest request) {
            // Use http client to call back to client, check that expected challenge is returned.
            final String mode = request.getParameter("hub.mode");
            final String topicUrl = request.getParameter("hub.topic");
            final String callbackUrl = request.getParameter("hub.callback");
            val req = new SubscribeRequest(mode, topicUrl, callbackUrl);
            logger.info("doSubscribe: Received request {} parsed into {}", request, req);
            subscribeCallback.accept(req);
            return HttpResponses.status(202);
        }

        public com.google.api.client.http.HttpResponse sendVerification(final SubscribeRequest req) throws IOException {
            final HttpRequestFactory requestFactory =
                    transport.createRequestFactory();
            logger.info("Sending verification to {}", req.callbackUrl);
            val url = new GenericUrl(req.callbackUrl);
            url.put("hub.mode", "subscribe");
            url.put("hub.topic", req.topicUrl);
            url.put("hub.challenge", "hello world");
            url.put("hub.lease_seconds", "45");
            final HttpRequest request =
                    requestFactory.buildGetRequest(url);
            request.setUnsuccessfulResponseHandler(
                    (HttpRequest request1, com.google.api.client.http.HttpResponse response, boolean retrySupported) -> false);
            request.setIOExceptionHandler(
                    (HttpRequest request1, boolean retrySupported) -> false);
            val response = request.execute();
            // Assume everything went OK, if not then the caller should be doing the actual validation.
            subscribers.put(req.topicUrl, req.callbackUrl);
            return response;
        }

        /**
         * Publish contents to particular topic URL (i.e. send notifications to registered clients).
         * @returns number of notifications sent out
         */
        public int publish(final String topicUrl, final String contents) throws IOException {
            val topicSubscribers = subscribers.get(topicUrl);
            if (topicSubscribers == null) {
                logger.warn("No subscribers found for topic {}", topicUrl);
                return 0;
            }

            for (val callbackUrl : topicSubscribers) {
                final HttpRequestFactory requestFactory =
                        transport.createRequestFactory();
                val url = new GenericUrl(callbackUrl);
                final HttpRequest request =
                        requestFactory.buildPostRequest(url, new ByteArrayContent("text/plain;encoding=utf-8", contents.getBytes(StandardCharsets.UTF_8)));
                request.setUnsuccessfulResponseHandler(
                        (HttpRequest request1, com.google.api.client.http.HttpResponse response, boolean retrySupported) -> false);
                request.setIOExceptionHandler(
                        (HttpRequest request1, boolean retrySupported) -> false);
                val response = request.execute();
                logger.info("Received response from subscriber: {}", response);
            }
            return topicSubscribers.size();
        }

        Hub(Consumer<SubscribeRequest> subscribeCallback) {
            this.subscribeCallback = subscribeCallback;
        }
    }

    @Test
    void testJenkinsJobSaveCausesSubscribe(JenkinsRule j, StaplerServer server) throws Exception {
        // TODO: Should we really have to set this here?
        //  it is required right now for the plugin to work.
        val cfg = JenkinsLocationConfiguration.get();
        assert cfg != null;
        cfg.setUrl(j.getURL().toString());
        cfg.save();
        final Lock lock = new ReentrantLock();
        final Condition cv = lock.newCondition();
        val req = ClosureVal.of(Hub.SubscribeRequest.class);
        // Construct hub with callback which will be invoked when subscription request occurs.
        val hub = new Hub(r -> {
            logger.info("Received request with");
            try (val l = LockGuard.from(lock)) {
                req.value = r;
                cv.signal();
            }
        });

        // Configure hub with hubUrl which will be server.baseUrl/subscribe
        hub.setHubUrl(fmt("{}/subscribe", server.getBaseUrl()));

        server.setCallback(hub);

        // Configure hub with topicUrl which can be anything it just needs to be consistent with what we publish with later
        val topicUrl = "http://example.com/feed";
        hub.setTopicUrl(topicUrl);

        // Initial topic added to job should be the {server URL}/gettopic
        FreeStyleProject project = j.createFreeStyleProject();
        project.setQuietPeriod(0);
        project.save();
        org.jvnet.hudson.test.JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setCssEnabled(false);
        logger.info("Getting configure page");
        HtmlPage page = wc.getPage(project, "configure");
        logger.info("Waiting for configure page");
        wc.waitForBackgroundJavaScriptStartingBefore(5000);
        HtmlForm form = page.getFormByName("config");
        form.getInputByName("io-jenkins-plugins-websub-WebSubTrigger").setChecked(true);
        HtmlButton button = page.querySelector(".yui-button.repeatable-add").querySelector("button");
        button.click();
        logger.info("Waiting for click 1");
        wc.waitForBackgroundJavaScriptStartingBefore(5000);

        HtmlInput topicInput = page.getElementByName("_.topicUrl");
        topicInput.setValueAttribute(fmt("{}/gettopic", server.getBaseUrl()));
        val els = form.getHtmlElementsByTagName("button");
        els.get(els.size() - 1).click();
        logger.info("Waiting for click 2");
        wc.waitForBackgroundJavaScriptStartingBefore(5000);

        // Then we expect that the plugin will request the discovery (invoking doGetTopic) followed by subscription (invoking doSubscribe)
        logger.info("Waiting for subscriber to subscribe.");
        try (val l = LockGuard.from(lock)) {
            // It was faster than the config page load.
            if (req.value != null) {
                logger.info("Subscriber already subscribed!");
            } else {
                boolean signalled = cv.await(5, TimeUnit.SECONDS);
                logger.info("Subscriber subscribed: {}", signalled);
            }
        }
        // After doSubscribe we need to (somehow) call sendVerification after the subscriber has finished verification with the SubscribeRequest and verify the response.
        val response = hub.sendVerification(req.value);
        logger.info("Response: {}", response);
        // Now our job should be triggered.
        hub.publish(topicUrl, "hello world");
        try {
            await().atMost(5, TimeUnit.SECONDS).until(() -> !project.getBuilds().isEmpty());
        } catch (ConditionTimeoutException ex) {
            logger.warn("Did not see build in time in time.");
        }

        logger.info("Empty builds: {}", project.getBuilds().isEmpty());
        // TODO: Export content of request as build variables and verify they
        //  are as expected on completed build.
    }
}
