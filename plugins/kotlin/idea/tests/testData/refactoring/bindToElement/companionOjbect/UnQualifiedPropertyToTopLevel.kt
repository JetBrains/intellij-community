// FILE: test/Test.kt
// BIND_TO test.bar.foo
package test

import test.foo.Foo.Companion.foo

fun test() {
    println(<caret>foo)
}

// FILE: test/foo/Foo.kt
package test.foo

class Foo {
    companion object {
       val foo: String = "foo"
    }
}

// FILE: test/bar/foo.kt
package test.bar

val foo: String = "foo"
