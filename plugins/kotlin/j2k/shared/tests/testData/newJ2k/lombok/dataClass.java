// !ADD_LOMBOK_ANNOTATIONS
// KTIJ-12602: @Data generates a constructor and public accessors, so the class must keep both
package test;

import lombok.Data;
import lombok.NonNull;

@Data
public class Pojo {
    private final int foo;
    private final long bar;
    private final String baz;

    @NonNull
    private String nonNull;

    private String withSetter;

    private final String initialized = "x";

    private static final String CONSTANT = "c";
}
