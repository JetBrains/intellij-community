package test

class A() {
    constructor(val <caret>name: String)
}

fun bar() {
    println(A(""))
}