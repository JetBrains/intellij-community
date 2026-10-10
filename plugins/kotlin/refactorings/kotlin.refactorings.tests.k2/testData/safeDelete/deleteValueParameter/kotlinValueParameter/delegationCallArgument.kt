package test

class A(val x: Int, <caret>y: Int) {
    constructor(s: String) : this(s.length, 0)
}