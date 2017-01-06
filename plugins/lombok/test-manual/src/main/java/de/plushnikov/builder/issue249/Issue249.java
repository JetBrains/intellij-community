package de.plushnikov.builder.issue249;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class Issue249<T> {
    T value;

    public static void main(String[] args) {
        final Issue249Builder<String> b = Issue249.<String>builder().value("a");
        final Issue249<String> t = b.build();
    }
}
