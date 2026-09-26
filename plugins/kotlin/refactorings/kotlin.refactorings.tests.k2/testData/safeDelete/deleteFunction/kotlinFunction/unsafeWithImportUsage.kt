package test

import test.NumberObject.numberFun

object NumberObject {
    fun num<caret>berFun() = 0
}

fun countUsages() {
    var vb = numberFun()
}