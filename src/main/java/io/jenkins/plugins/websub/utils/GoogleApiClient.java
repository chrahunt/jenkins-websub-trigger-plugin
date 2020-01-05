package io.jenkins.plugins.websub.utils;

import com.google.api.client.http.HttpResponse;
import com.google.api.client.util.Charsets;
import com.google.common.io.CharStreams;

import java.io.IOException;
import java.io.InputStreamReader;

final public class GoogleApiClient {
    private GoogleApiClient() {}

    public static String getHttpResponseBody(final HttpResponse response) throws IOException {
        String charset = response.getContentEncoding();
        if (charset == null) {
            charset = Charsets.UTF_8.name();
        }
        return CharStreams.toString(new InputStreamReader(response.getContent(), charset));
    }
}
