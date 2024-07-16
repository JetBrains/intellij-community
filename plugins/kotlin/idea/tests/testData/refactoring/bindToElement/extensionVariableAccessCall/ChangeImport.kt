// FILE: test/ConflictingImport.kt
// BIND_TO test.bar.barFoo
package test

import test.foo.fooBar

fun foo() {
    0.<caret>fooBar()
}

// FILE: test/foo/A.kt
package test.foo

val fooBar: Any.() -> Unit = { }

// FILE: test/bar/A.kt
package test.bar

val barFoo: Any.() -> Unit = { }