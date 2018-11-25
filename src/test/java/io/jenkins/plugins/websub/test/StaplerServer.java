package io.jenkins.plugins.websub.test;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.WebApp;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.jetty.webapp.WebAppContext;

import javax.servlet.ServletContext;
import java.net.URL;
import java.util.function.Function;

import static io.jenkins.plugins.websub.WebSubUtils.fmt;

/**
 * Brings up Jetty server to be used as endpoint for test cases requiring a server.
 *
 * Later we may use a similar construction based on ngrok or another service for
 * testing against websub.rocks.
 */
public class StaplerServer implements AutoCloseable {
    public static class Resolver implements ParameterResolver, AfterEachCallback {
        private static final String key = "jenkins-instance";
        private static final ExtensionContext.Namespace ns =
                ExtensionContext.Namespace.create(Resolver.class);

        @Override
        public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
                throws ParameterResolutionException {
            return parameterContext.getParameter().getType().equals(StaplerServer.class);
        }

        @Override
        public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
                throws ParameterResolutionException {
            return extensionContext.getStore(ns).getOrComputeIfAbsent(
                    key, key -> {
                        try {
                            return new StaplerServer();
                        } catch(Exception e) {
                            throw new RuntimeException(e);
                        }
                    }, StaplerServer.class);
        }

        @Override
        public void afterEach(ExtensionContext context) {
            StaplerServer server = context.getStore(ns).remove(key, StaplerServer.class);
            if (server != null)
                server.close();
        }
    }
    final private org.mortbay.jetty.Server server = new org.mortbay.jetty.Server();
    final private URL baseUrl;
    final private WebApp webApp;

    private StaplerServer() throws Exception {
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

    public void setCallback(Object callback) {
        webApp.setApp(callback);
    }

    public void setCallback(Function<String, Object> callback) {
        webApp.setApp(callback.apply(getBaseUrl().toString()));
    }

    public URL getBaseUrl() { return baseUrl; }

    @Override
    public void close() {
        server.setGracefulShutdown(50);
    }
}
