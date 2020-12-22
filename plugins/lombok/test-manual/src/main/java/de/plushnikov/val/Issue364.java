package de.plushnikov.val;

import lombok.val;

import java.util.stream.Stream;

public class Issue364 {

    public static void main(String... args) {
        Stream.of("foo").map(arg -> {
            val bar = cast(arg, String.class);
            return bar;
        });
    }

    private static <T> T cast(Object value, Class<T> clazz) {
        return clazz.cast(value);
    }
}
