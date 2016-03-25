package de.plushnikov.constructor;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

public class RequiredArgsConstructorWithGeneric136<T> {

    @Getter
    @RequiredArgsConstructor(staticName = "of2")
    private static class Foo<T> {
        private final T object;
        private final int i;

        static <T> Foo<T> of(T object, int i) {
            return new Foo<T>(object, i);
        }
    }

    private <D> Foo<D> createFoo(D t, int i) {
        return new Foo<>(t, i);
    }

    public static void main(String[] args) {
        Foo<String> stringFoo = new Foo<>("", 2);

        Foo<String> foo1 = Foo.of("String2", 123);
        Foo<String> foo2 = Foo.of2("String2", 4423);

        System.out.println(stringFoo);
        System.out.println(foo1);
        System.out.println(foo2);
    }
}