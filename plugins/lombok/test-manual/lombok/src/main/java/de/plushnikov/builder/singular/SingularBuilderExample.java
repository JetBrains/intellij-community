package de.plushnikov.builder.singular;

import lombok.Builder;
import lombok.Singular;
import lombok.ToString;

import java.util.Arrays;
import java.util.Set;

@Builder
@ToString
public class SingularBuilderExample {
    private String name;
    private int age;
    @Singular
    private Set<String> occupations;

    public static void main(String[] args) {
        SingularBuilderExampleBuilder builder = SingularBuilderExample.builder();
        SingularBuilderExample builderExample = builder.occupations(Arrays.asList("1234", "2345")).build();
        System.out.println(builderExample);
    }
}