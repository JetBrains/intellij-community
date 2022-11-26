public class J {
    void foo() {
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
    }
}