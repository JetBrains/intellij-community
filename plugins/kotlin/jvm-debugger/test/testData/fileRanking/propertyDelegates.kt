// WITH_STDLIB

//FILE: a/a.kt
package foo

class A {
    val a by lazy {
        val a = 5
        val b = 2
        ""
    }
}
// PRODUCED_CLASS_NAMES: foo.A, foo.A$a$2(optional)

//FILE: b/a.kt
package bar

class B {
    val a by lazy {
        val b = 0
    }
}
// PRODUCED_CLASS_NAMES: bar.B, bar.B$a$2(optional)