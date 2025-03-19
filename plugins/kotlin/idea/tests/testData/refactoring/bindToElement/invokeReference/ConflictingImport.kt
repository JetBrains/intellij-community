// FILE: test/ConflictingImport.kt
// BIND_TO test.bar.invoke
package test

import test.foo.invoke

fun foo() {
    0<caret>()
}

// FILE: test/foo/A.kt
package test.foo

fun Int.invoke() { }

// FILE: test/bar/A.kt
package test.bar

fun Int.invoke() { }