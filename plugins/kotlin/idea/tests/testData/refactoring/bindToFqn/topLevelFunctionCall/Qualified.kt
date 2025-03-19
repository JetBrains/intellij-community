// FILE: test/Qualified.kt
// BIND_TO test.bar.fooBar
package test

fun foo() {
    test.foo.<caret>fooBar()
}

// FILE: test/foo/Test.kt
package test.foo

fun fooBar() { }

// FILE: test/bar/Test.kt
package test.bar

fun fooBar() { }