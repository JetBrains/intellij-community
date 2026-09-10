// FILE: test/UnQualified.kt
// BIND_TO test.bar.Container.fooBar
package test

import test.foo.fooBar

fun caller() {
    foo<caret>Bar()
}

// FILE: test/bar/Container.kt
package test.bar

object Container {
    fun fooBar() { }
}

// FILE: test/foo/Test.kt
package test.foo

fun fooBar() { }
