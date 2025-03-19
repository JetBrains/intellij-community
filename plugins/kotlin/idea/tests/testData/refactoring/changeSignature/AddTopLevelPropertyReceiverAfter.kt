package test

class A

open var A.p: Int
    get() = 1
    set(value: Int) {}

fun test() {
    val t = A().p
    A().p = 1
}
