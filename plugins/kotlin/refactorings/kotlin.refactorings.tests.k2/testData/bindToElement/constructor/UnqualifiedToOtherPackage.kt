// FILE: test/Constructor.kt
// BIND_TO test.bar.A
package test

import test.foo.A

fun test(){
    val a = <caret>A()
}

// FILE: test/foo/A.kt
package test.foo

class A

// FILE: test/bar/A.kt
package test.bar

class A
