// FILE: test/ImportReferenceWithAlias.kt
// BIND_TO test.bar.A
package test

import test.foo.<caret>A as B

fun foo() {
    val x = B()
}

// FILE: test/foo/A.kt
package test.foo

class A { }

// FILE: test/bar/A.kt
package test.bar

class A { }