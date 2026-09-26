// FILE: test/Test.kt
// BIND_TO test.Bar.FOO
package test

fun test() {
    Foo.<caret>FOO
}

enum class Foo {
    FOO, BAR
}

enum class Bar {
    FOO, BAR
}