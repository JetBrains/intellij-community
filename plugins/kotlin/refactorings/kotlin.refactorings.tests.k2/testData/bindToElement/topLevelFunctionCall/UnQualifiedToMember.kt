// FILE: test/UnQualified.kt
// BIND_TO test.Container.fooBar
package test

import test.foo.fooBar

class Container {
    fun fooBar() { }
}

fun caller() {
    <caret>fooBar()
}

// FILE: test/foo/Test.kt
package test.foo

fun fooBar() { }
