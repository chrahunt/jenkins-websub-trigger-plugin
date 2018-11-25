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

import static io.jenkins.plugins.websub.WebSubUtils.fmt;

/**
 * Receives HTTP requests and dispatch to WebSubSubscriber if applicable.
 */
@Extension
// Used by Jenkins.
@SuppressWarnings("unused")
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
     */
    public HttpResponse doCallback(final StaplerRequest request) {
        logger.info("doCallback()");
        try {
            return WebSubSharedResources.getInstance().getClient().handleRequest(request);
        } catch (Exception e) {
            // If client retrieval failed.
            logger.error("Could not retrieve client. Caused by: ", e);
            return HttpResponses.status(500);
        }
    }

    /**
     * Endpoint initialization.
     */
    @Initializer(after=InitMilestone.JOB_LOADED)
    public static void init() {
        // For now we just pass our prefix along. The SharedResources handle the case where the
        // Jenkins URL is not set.
        val resources = WebSubSharedResources.getInstance();
        resources.setPrefix(URL_PREFIX);
    }

    /**
     * Indicate whether POST requests should be excluded from CSRF protection.
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
            return true;
        }
        return false;
    }

    @Override
    public String getIconFileName() { return null; }

    @Override
    public String getDisplayName() { return null; }

    @Override
    public String getUrlName() { return PLUGIN_URL_NAME; }

}
