// FILE: test/bar/Foo.kt
// BIND_TO test.B
package test.bar

import test.B

fun foo() {
    val x: test.foo.<caret>A = B()
}

// FILE: test/foo/A.kt
package test.foo

interface A { }

// FILE: test/B.kt
package test

class B : A { }