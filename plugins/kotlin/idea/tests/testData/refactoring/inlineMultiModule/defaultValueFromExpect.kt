// PLATFORM: Common
// FILE: Foo.kt
expect fun foo(enabled: Boolean = true)

// PLATFORM: Jvm
// FILE: Foo.kt
// MAIN
actual fun foo(enabled: Boolean) {
    bar(enabled)
}
fun bar(enabled: Boolean) {}

fun usage() {
    f<caret>oo()
}
