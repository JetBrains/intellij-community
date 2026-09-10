// FILE: test/Qualified.kt
// BIND_TO test.bar.fooBar
package test

fun foo() {
    val x = test.foo.<caret>fooBar
}

// FILE: test/foo/Test.kt
package test.foo

val fooBar = 0

// FILE: test/bar/Test.kt
package test.bar

val fooBar = 0