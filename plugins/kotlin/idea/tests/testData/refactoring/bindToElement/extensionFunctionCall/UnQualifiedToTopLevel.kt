// FILE: test/UnQualified.kt
// BIND_TO test.bar.fooBar
package test

import test.foo.fooBar

fun Any.foo() {
    <caret>fooBar()
}

// FILE: test/foo/Test.kt
package test.foo

fun Any.fooBar() { }

// FILE: test/bar/Test.kt
package test.bar

fun fooBar() { }