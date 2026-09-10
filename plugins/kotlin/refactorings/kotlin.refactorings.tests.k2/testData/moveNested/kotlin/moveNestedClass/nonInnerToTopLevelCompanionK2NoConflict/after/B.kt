package test

import test.A.Companion.extBar
import test.A.Companion.extFoo

class B {
    fun test() {
        1.extFoo(1.extBar)
    }
}