package test

class A(val x: Int) {
    <caret>constructor() : this(0)

    fun f() {}
}
