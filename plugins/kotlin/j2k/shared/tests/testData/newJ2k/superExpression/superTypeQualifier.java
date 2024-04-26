// IGNORE_K2
interface A {
    default void foo() {
    }
}

interface B {
    default void foo() {
    }
}

class C implements A, B {
    public void foo() {
        A.super.foo();
    }
}