// FILE: test/UnQualified.kt
// BIND_TO test.bar.fooBar
package test

import test.foo.fooBar

fun foo() {
    val x = <caret>fooBar
}

// FILE: test/foo/Test.kt
package test.foo

val fooBar = 0

// FILE: test/bar/Test.kt
package test.bar

val fooBar = 0