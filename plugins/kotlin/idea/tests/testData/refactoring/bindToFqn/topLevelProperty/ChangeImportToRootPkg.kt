// FILE: test/ChangeImport.kt
// BIND_TO y
package test

fun foo() {
    val x = <caret>y
}

// FILE: test/foo/A.kt
package test.foo

val y = 0

// FILE: A.kt
val y = 0