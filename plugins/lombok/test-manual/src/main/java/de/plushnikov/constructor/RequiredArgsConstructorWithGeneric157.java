package de.plushnikov.constructor;

import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

public class RequiredArgsConstructorWithGeneric157 {

    @RequiredArgsConstructor(staticName = "of")
    private static class Foo<T, E extends Exception> {
        private final Map<T, E> bar;

        Map<T, E> buildBar() {
            return bar;
        }
    }

    public static void main(String[] args) {
        Foo<String, IllegalArgumentException> foo = new Foo<>(new HashMap<String, IllegalArgumentException>());
        System.out.println(foo);

        HashMap<Integer, IllegalStateException> hashMap = new HashMap<>();
        Foo<Integer, IllegalStateException> myFoo = Foo.of(hashMap);
        Map<Integer, IllegalStateException> bar = myFoo.buildBar();
        System.out.println(bar);

        Foo<Integer, NullPointerException> exceptionFoo = Foo.of(new HashMap<Integer, NullPointerException>());
        System.out.println(exceptionFoo.buildBar());
    }
}