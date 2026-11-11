package test

actual class Foo constructor(i: Int) {
    constructor(s: String) : this()
}