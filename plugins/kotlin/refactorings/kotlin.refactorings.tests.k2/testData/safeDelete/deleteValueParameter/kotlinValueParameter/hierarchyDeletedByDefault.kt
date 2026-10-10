package test

open class A {
    open fun foo(<caret>unusedParam: Int) {

    }
}

class B : A() {
    override fun foo(unusedParam: Int) {

    }
}
