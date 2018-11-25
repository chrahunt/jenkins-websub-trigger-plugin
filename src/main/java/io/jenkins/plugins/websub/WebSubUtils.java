package io.jenkins.plugins.websub;

import com.google.api.client.http.HttpResponse;
import com.google.api.client.util.Charsets;
import com.google.common.collect.Iterators;
import com.google.common.io.CharStreams;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import javax.annotation.Nullable;
import lombok.Value;
import org.slf4j.helpers.MessageFormatter;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class WebSubUtils {
    private WebSubUtils() {}

    /**
     * String formatting helper
     * @param formatString string with plain {} for substitutions
     * @param objects objects to be substituted into the formatString
     * @return formatted string
     */
    public static String fmt(final String formatString, final Object... objects) {
        return MessageFormatter.arrayFormat(formatString, objects).getMessage();
    }

    public static String getHttpResponseBody(final HttpResponse response) throws IOException {
        String charset = response.getContentEncoding();
        if (charset == null)
            charset = Charsets.UTF_8.name();
        return CharStreams.toString(new InputStreamReader(response.getContent(), charset));
    }

    /**
     * Enumeration to Stream
     */
    public static <T> Stream<T> toStream(Enumeration<T> e) {
        // https://stackoverflow.com/a/27144804/1698058
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(Iterators.forEnumeration(e), Spliterator.IMMUTABLE), false);
    }

    @Value(staticConstructor="of")
    public static class Pair<K, V> {
        K key;
        V value;
    }

    /**
     * Helper for getting values out of callbacks.
     *
     * We hide the constructor because IDEs typically hide the right set of angle
     * brackets.
     */
    public static class ClosureVal<T> {
        public T value;
        private ClosureVal() {}
        private ClosureVal(T value) { this.value = value; }

        public static <T> ClosureVal<T> of(final Class<T> type) {
            return new ClosureVal<>();
        }

        public static <T> ClosureVal<T> of(final T val) {
            return new ClosureVal<>(val);
        }

        /**
         * Overload if we want to store a class instance.
         */
        public static <T> ClosureVal<T> ofClass(final T val) {
            return new ClosureVal<>(val);
        }
    }

    public static <T> Optional<T> cast(@Nullable Object o, Class<T> type) {
        if (type.isInstance(o)) {
            return Optional.of(type.cast(o));
        }
        return Optional.empty();
    }

    public static class LockGuard <T extends Lock> implements AutoCloseable {
        final T lock;
        private LockGuard(final T lock) {
            this.lock = lock;
            this.lock.lock();
        }

        public static <T extends Lock> LockGuard<T> from(final T lock) {
            return new LockGuard<>(lock);
        }

        public void close() {
            this.lock.unlock();
        }
    }
}
