package de.plushnikov.wither;

import lombok.experimental.Wither;

import javax.annotation.Nullable;

@Wither
public class WitherFieldNames {
    private boolean isOne;
    private String isTwo;

    public WitherFieldNames(boolean a, String b) {
        System.out.println("ssss");
    }

    public static void main(String[] args) {
        new WitherFieldNames(true, "").withOne(true).withIsTwo("b");
    }
}