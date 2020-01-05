package io.jenkins.plugins.websub.utils;

import com.google.common.collect.Iterators;
import java.util.Enumeration;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

final class Collections {
    private Collections() {}

    /*
     * Enumeration to Stream
     */
    public static <T> Stream<T> toStream(Enumeration<T> e) {
        // https://stackoverflow.com/a/27144804/1698058
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(Iterators.forEnumeration(e), Spliterator.IMMUTABLE), false);
    }
}
