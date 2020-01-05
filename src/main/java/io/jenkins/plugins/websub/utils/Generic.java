package io.jenkins.plugins.websub.utils;

import java.util.Optional;
import java.util.concurrent.locks.Lock;
import javax.annotation.Nullable;
import lombok.Value;
import org.slf4j.helpers.MessageFormatter;

final public class Generic {
    private Generic() {}

    @Value(staticConstructor = "of")
    public static class Pair<K, V> {
        K key;
        V value;
    }

    /**
     * String formatting helper.
     *
     * @param formatString string with plain {} for substitutions
     * @param objects objects to be substituted into the formatString
     * @return formatted string
     */
    public static String fmt(final String formatString, final Object... objects) {
        return MessageFormatter.arrayFormat(formatString, objects).getMessage();
    }

    /**
     * Helper for getting values out of synchronous callbacks.
     */
    public static class ClosureVal<T> {
        public T value;
        // Constructor isn't useful, since we want to be able to do
        // val v = ClosureVal(String.class);
        // Using the plain constructor results in IntelliJ hiding the type, like
        // val v = ClosureVal<~>();
        private ClosureVal() {}
        private ClosureVal(T value) { this.value = value; }

        public static <T> ClosureVal<T> of(final Class<T> type) {
            return new ClosureVal<>();
        }

        public static <T> ClosureVal<T> of(final T val) {
            return new ClosureVal<>(val);
        }

        /**
         * Overload in case we want to store a class instance.
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
