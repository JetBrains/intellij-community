package de.plushnikov.test;

import lombok.Getter;

@Getter
public class SomeTest {

    private int a;

    public static void main(String[] args) {
        SomeTest someTest = new SomeTest();
        someTest.a = 1;
        System.out.println(someTest.a);
    }
}
