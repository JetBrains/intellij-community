// FILE: test/ConflictingImport.kt
// BIND_TO test.bar.fooBar
package test

import test.foo.fooBar

fun foo() {
    0.<caret>fooBar()
}

// FILE: test/foo/A.kt
package test.foo

fun Any.fooBar() { }

// FILE: test/bar/A.kt
package test.bar

fun Any.fooBar() { }