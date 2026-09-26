// FILE: test/Constructor.kt
// BIND_TO test.bar.A
package test

fun test(){
    val a = test.foo.<caret>A()
}

// FILE: test/foo/A.kt
package test.foo

class A

// FILE: test/bar/A.kt
package test.bar

class A
