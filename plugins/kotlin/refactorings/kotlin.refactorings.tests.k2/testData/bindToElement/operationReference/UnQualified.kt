// FILE: test/UnQualified.kt
// BIND_TO test.bar.barFoo
package test

import test.foo.fooBar

fun foo() {
    0 <caret>fooBar 1
}

// FILE: test/foo/Test.kt
package test.foo

infix fun Int.fooBar(other: Int) {
    this + other
}

// FILE: test/bar/Test.kt
package test.bar

infix fun Int.barFoo(other: Int) {
    this + other
}