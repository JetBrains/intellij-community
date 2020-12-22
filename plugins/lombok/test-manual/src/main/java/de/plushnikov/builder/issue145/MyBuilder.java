package de.plushnikov.builder.issue145;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MyBuilder {

    private String myString;

    public static MyBuilderBuilder builder() {
        return new MyBuilderBuilder().myString("my  default value");
    }
}
