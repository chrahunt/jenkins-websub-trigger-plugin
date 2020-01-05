package io.jenkins.plugins.websub.utils;

import com.google.api.client.http.UrlEncodedParser;
import com.google.common.collect.ImmutableMultimap;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.io.IOUtils;

import static io.jenkins.plugins.websub.utils.Collections.toStream;

final public class JavaxServlet {
    private JavaxServlet() {}

    public static ImmutableMultimap<String, String> getRequestHeaders(final HttpServletRequest request) {
        final ImmutableMultimap.Builder<String, String> builder = ImmutableMultimap.builder();
        toStream(request.getHeaderNames())
                .forEach(k -> builder.putAll(k, Collections.list(request.getHeaders(k))));
        return builder.build();
    }

    public static ImmutableMultimap<String, String> getRequestParams(final HttpServletRequest request) {
        Map<String, List<String>> data = new HashMap<>();
        UrlEncodedParser.parse(request.getQueryString(), data);

        ImmutableMultimap.Builder<String, String> builder = ImmutableMultimap.builder();
        data.forEach(builder::putAll);
        return builder.build();
    }

    public static String getRequestBody(final HttpServletRequest request) throws IOException {
        // TODO: Enforce limits on payload size.
        BufferedReader reader = request.getReader();
        return IOUtils.toString(reader);
    }
}
