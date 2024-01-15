interface A {
    default void foo() { }
}

interface B extends A {
    default void foo() {
        A.super.foo();
    }
}