package de.plushnikov.builder;

import lombok.Builder;

public class BuilderExceptions {
    @Builder
    private static void foo(int i) throws Exception {
        System.out.println("sss");
    }

    public static void main(String[] args) {
        try {
            builder().i(2).build();
        } catch (Exception e) {
        }
    }
}
