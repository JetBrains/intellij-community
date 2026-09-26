// FILE: test/ChangeImport.kt
// BIND_TO A
package test

fun foo() {
    val x = <caret>A()
}

// FILE: test/foo/A.kt
package test.foo

class A { }

// FILE: A.kt
class A { }