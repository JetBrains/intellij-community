// FILE: test/UnaryOperator.kt
// BIND_TO test.bar.not
package test

import test.foo.not

fun foo(x: String): Boolean {
    return <caret>!x
}

// FILE: test/foo/Test.kt
package test.foo

operator fun String.not() = false

// FILE: test/bar/Test.kt
package test.bar

operator fun String.not() = false