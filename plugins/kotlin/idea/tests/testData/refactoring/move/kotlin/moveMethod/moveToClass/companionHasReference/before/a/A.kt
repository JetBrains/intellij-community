package a

import b.B

class A {
    fun dummy(c: Companion) {}

    companion object {
        fun foo(o: B) {}
    }
}