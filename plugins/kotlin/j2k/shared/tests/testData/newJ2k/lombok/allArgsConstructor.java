// !ADD_LOMBOK_ANNOTATIONS
// KTIJ-19423: @AllArgsConstructor takes every field, not only the required ones
package test;

import lombok.AllArgsConstructor;
import lombok.NonNull;

@AllArgsConstructor
public class Everything {
    private final String required;

    private String mutable;

    @NonNull
    private String nonNull;

    private final String initialized = "skipped";

    private static String shared;
}

@AllArgsConstructor
class NoInstanceFields {
    private static final String CONSTANT = "c";
}
