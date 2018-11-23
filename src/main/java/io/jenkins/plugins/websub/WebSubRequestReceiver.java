package io.jenkins.plugins.websub;

import hudson.Extension;
import hudson.model.UnprotectedRootAction;
import hudson.security.csrf.CrumbExclusion;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Receive HTTP requests and dispatch to WebSubSubscriber if applicable.
 */
@Extension
public class WebSubRequestReceiver
        extends CrumbExclusion implements UnprotectedRootAction {

    private static final String PLUGIN_URL_NAME = "websub-trigger";
    // URL format: /{prefix}/{uuid}/notify
    private static final String URL_PREFIX =
            String.format("/{}/", PLUGIN_URL_NAME);

    /**
     * Stapler action method invoked when one of our callback methods is
     * invoked.
     */
    public HttpResponse doCallback(final StaplerRequest request) {
        return WebSubSharedResources.getInstance().getClient().handleRequest(request);
    }

    @Override
    public boolean process(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain chain)
            throws IOException, ServletException {
        final String path = request.getPathInfo();
        if (path != null && path.startsWith(URL_PREFIX)) {
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
    public String getUrlName() { return null; }
}
