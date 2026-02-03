// FILE: test/UnQualified.kt
// BIND_TO test.bar.fooBar
package test

import test.foo.fooBar

fun foo() {
    <caret>fooBar()
}

// FILE: test/foo/Test.kt
package test.foo

fun fooBar() { }

// FILE: test/bar/Test.kt
package test.bar

fun fooBar() { }