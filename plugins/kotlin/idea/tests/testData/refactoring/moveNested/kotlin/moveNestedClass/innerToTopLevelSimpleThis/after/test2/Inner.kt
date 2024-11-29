package test2

import test1.Test

class Inner(private val a: Test) {
    fun foo() {
        println(a)
    }
}