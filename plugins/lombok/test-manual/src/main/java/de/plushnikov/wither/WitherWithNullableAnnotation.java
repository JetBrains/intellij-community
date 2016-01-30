package de.plushnikov.wither;

import lombok.experimental.Wither;

import javax.annotation.Nullable;

@Wither
public class WitherWithNullableAnnotation {
    @Wither(lombok.AccessLevel.NONE)
    private boolean isNone;

    @Nullable
    private String myPublic;

    public WitherWithNullableAnnotation(boolean isNone, String isPublic) {
        System.out.println("ssss");
    }

    public static void main(String[] args) {
        WitherWithNullableAnnotation wither = new WitherWithNullableAnnotation(true, "").withMyPublic("aaa");
        wither.withMyPublic("bbb");
    }
}