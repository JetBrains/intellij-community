package de.plushnikov.builder.issue393;

import java.util.Map;
import lombok.Builder;
import lombok.Singular;

@Builder
public class BuilderExample393 {

    @Singular("myMap")
    private Map<String, String> myMap;
}
