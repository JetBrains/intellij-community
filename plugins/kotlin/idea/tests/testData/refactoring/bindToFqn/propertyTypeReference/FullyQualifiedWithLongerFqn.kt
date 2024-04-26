// FILE: test/bar/Foo.kt
// BIND_TO test.foo.bar.B
package test.bar

import test.foo.bar.B

fun foo() {
    val x: test.foo.<caret>A = B()
}

// FILE: test/foo/A.kt
package test.foo

interface A { }

// FILE: test/foo/bar/B.kt
package test.foo.bar

class B : A { }