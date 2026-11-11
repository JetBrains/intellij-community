package test

actual class Foo constructor(i: Int) {
    constructor() : this()
    constructor(s: String) : this()
}