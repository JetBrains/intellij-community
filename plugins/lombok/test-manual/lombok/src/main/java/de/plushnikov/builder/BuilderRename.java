package de.plushnikov.builder;

import lombok.Builder;

@Builder
public class BuilderRename {
    private String name;

    public static void main(String[] args) {
        BuilderRename test = BuilderRename.builder().name("Test").build();
        System.out.println(test.toString());
    }
}
