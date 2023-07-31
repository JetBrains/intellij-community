// FILE: test/ChangeImport.kt
// BIND_TO test.bar.A
package test

import test.foo.<caret>A

fun foo() {
    val x = A()
}

// FILE: test/foo/A.kt
package test.foo

class A { }

// FILE: test/bar/A.kt
package test.bar

class A { }