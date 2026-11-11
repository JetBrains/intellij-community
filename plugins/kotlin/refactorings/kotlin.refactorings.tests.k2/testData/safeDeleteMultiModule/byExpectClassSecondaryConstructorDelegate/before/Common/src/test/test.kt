package test

expect class Foo constructor(i: Int) {
    co<caret>nstructor() : this()
    constructor(s: String) : this()
}