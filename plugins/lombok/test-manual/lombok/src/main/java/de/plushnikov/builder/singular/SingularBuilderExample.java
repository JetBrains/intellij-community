package de.plushnikov.builder.singular;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import lombok.Builder;
import lombok.Singular;
import lombok.experimental.Accessors;

import java.util.*;

@Builder
public class SingularBuilderExample {
    private String name;
    private int age;

    @Singular
    private List<String> occupations;

    @Singular
    private ImmutableSet<Float> occupation2s;

    @Singular
    private Map<Integer, Float> cars;

    @Singular
    private ImmutableMap<String, Float> car2s;

    public static void main(String[] args) {
        SingularBuilderExampleBuilder builder = SingularBuilderExample.builder().age(34);
        System.out.println(builder.occupations(Arrays.asList("1234", "2345")).build());
        System.out.println(builder.occupation("aaa").build());

        System.out.println(builder.occupation2(2.0f).build());
        System.out.println(builder.occupation2(2.0f).build());

        System.out.println(builder.cars(new HashMap<Integer, Float>()).build());
        System.out.println(builder.car(1, 2.0f).build());

        System.out.println(builder.car2s(new HashMap<String, Float>()).build());
        System.out.println(builder.car2("1", 2.0f).build());
    }

}