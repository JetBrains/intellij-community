// FILE: test/Test.kt
// BIND_TO test.bar.Bar.FOO
package test

import test.foo.Foo.FOO

fun test() {
    println(<caret>FOO)
}

// FILE: test/foo/Foo.kt
package test.foo

enum class Foo {
    FOO, BAR
}

// FILE: test/bar/foo.kt
package test.bar

enum class Bar {
    FOO, BAR
}
