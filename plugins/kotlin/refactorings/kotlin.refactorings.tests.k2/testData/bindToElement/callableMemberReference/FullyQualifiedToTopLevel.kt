// BIND_TO test.C
package test

class A {
    class B { }
}

class C { }

fun foo() {
    val x = test.A::<caret>B
}