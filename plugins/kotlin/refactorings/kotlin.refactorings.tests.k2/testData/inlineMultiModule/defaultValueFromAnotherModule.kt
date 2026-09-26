// PLATFORM: Common
// FILE: Foo.kt
fun bar(enabled: Boolean = defaultValue()) {}
fun foo() {
    bar(defaultValue())
}
fun defaultValue() = true

// PLATFORM: Jvm
// FILE: Foo.kt
// MAIN

fun usage() {
    f<caret>oo()
}
