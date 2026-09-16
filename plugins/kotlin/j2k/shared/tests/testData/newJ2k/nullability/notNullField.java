// KTIJ-39470: a not-null annotation on a field must remove the `?` from the property type
package test;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Test {
    @NotNull
    private String notNullField;

    @NotNull
    private Integer notNullBoxedField;

    @NotNull
    private String initializedInConstructor;

    @Nullable
    private String nullableField;

    private String plainField;

    public Test(@NotNull String initializedInConstructor) {
        this.initializedInConstructor = initializedInConstructor;
    }

    public void use() {
        System.out.println(notNullField.length());
        System.out.println(notNullBoxedField + nullableField + plainField);
    }
}
