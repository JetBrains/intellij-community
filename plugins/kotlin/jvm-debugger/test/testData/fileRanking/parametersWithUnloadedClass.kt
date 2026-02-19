//FILE: a/a.kt
package a

import c.*

class B : A<SomeImpl>() {
    override fun x(some: SomeImpl) {
        foo()
    }
}
// PRODUCED_CLASS_NAMES: a.B

// DO_NOT_LOAD: a.SomeImpl
class SomeImpl : Some()

//FILE: b/a.kt
package b

import c.*

class B : A<Some>() {
    override fun x(some: Some) {
        foo()
    }
}
// PRODUCED_CLASS_NAMES: b.B

//FILE: c/c.kt
package c

open class Some

abstract class A<T: Some> {
    abstract fun x(a: T)
}

fun foo() {}
// PRODUCED_CLASS_NAMES: c.Some, c.A, c.CKt