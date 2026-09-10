package test2

import test1.Test

class Inner(private val test: Test) {
    fun foo() {
        println(test)
    }
}