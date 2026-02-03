// FILE: test/ConflictingImport.kt
// BIND_TO test.bar.get
package test

import test.foo.get

fun foo() {
    0<caret>[0]
}

// FILE: test/foo/A.kt
package test.foo

fun Int.get(other: Int) { }

// FILE: test/bar/A.kt
package test.bar

fun Int.get(other: Int) { }