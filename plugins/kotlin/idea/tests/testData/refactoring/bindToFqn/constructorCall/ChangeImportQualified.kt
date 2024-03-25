// FILE: test/ChangeImport.kt
// BIND_TO A
package test

fun foo() {
    val x = test.foo.<caret>A()
}

// FILE: test/foo/A.kt
package test.foo

class A { }

// FILE: test/bar/A.kt
class A { }