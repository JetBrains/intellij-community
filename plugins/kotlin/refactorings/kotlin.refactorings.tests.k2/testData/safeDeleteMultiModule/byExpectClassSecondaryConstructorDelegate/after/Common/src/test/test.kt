package test

expect class Foo constructor(i: Int) {
    constructor(s: String) : this()
}