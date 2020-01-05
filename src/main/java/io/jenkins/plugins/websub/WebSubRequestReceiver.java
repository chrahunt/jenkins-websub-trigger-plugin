package io.jenkins.plugins.websub;

import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.UnprotectedRootAction;
import hudson.security.csrf.CrumbExclusion;
import hudson.util.HttpResponses;
import lombok.val;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.jenkins.plugins.websub.utils.Generic.fmt;

/**
 * Exposes listening endpoint on Jenkins
 * Receives HTTP requests and dispatch to WebSubSubscriber if the request path
 * matches our plugin prefix.
 */
@Extension
@SuppressWarnings("unused") // Used by Jenkins.
public class WebSubRequestReceiver
        extends CrumbExclusion implements UnprotectedRootAction {
    private static final Logger logger = LoggerFactory.getLogger(WebSubRequestReceiver.class);

    private static final String PLUGIN_URL_NAME = "websub-trigger";
    // URL format: {jenkins-base-url}{prefix}/callback
    // jenkins-base-url always has trailing /.
    private static final String URL_PREFIX = fmt("{}/callback", PLUGIN_URL_NAME);

    /**
     * Stapler action method invoked when one of our callback methods is
     * requested.
     *
     * This may be in response to a subscription request or a routine notification.
     *
     * ".../callback" maps to doCallback
     */
    public HttpResponse doCallback(final StaplerRequest request) {
        logger.debug("doCallback()");
        try {
            return WebSubSharedResources.getInstance().getClient().handleRequest(request);
        } catch (Exception e) {
            // If client retrieval failed.
            logger.error("Could not retrieve client. Caused by: ", e);
            return HttpResponses.status(500);
        }
    }

    @Initializer(after=InitMilestone.JOB_LOADED)
    public static void init() {
        // For now we just pass our prefix along. The SharedResources handle the case where the
        // Jenkins URL is not set.
        val resources = WebSubSharedResources.getInstance();
        resources.setPrefix(URL_PREFIX);
    }

    /**
     * Pass-thru requests applicable to our plugin, do not require CSRF token.
     */
    @Override
    public boolean process(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain chain)
            throws IOException, ServletException {
        final String path = request.getPathInfo();
        if (path != null && path.startsWith("/" + URL_PREFIX)) {
            // We are responsible for invoking the rest of the chain.
            chain.doFilter(request, response);
            // No CSRF token required.
            return true;
        }
        return false;
    }

    @Override
    public String getIconFileName() { return null; }

    @Override
    public String getDisplayName() { return null; }

    /**
     * Requests starting with the returned base path will get mapped to our object per
     * https://stapler.kohsuke.org/reference.html.
     * @return base URL
     */
    @Override
    public String getUrlName() { return PLUGIN_URL_NAME; }
}