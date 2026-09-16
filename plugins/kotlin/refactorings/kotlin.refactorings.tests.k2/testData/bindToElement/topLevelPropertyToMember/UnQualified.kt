// FILE: test/UnQualified.kt
// BIND_TO test.Container.value
package test

import test.foo.value

class Container {
    val value: Int = 0
}

fun caller() {
    println(<caret>value)
}

// FILE: test/foo/Test.kt
package test.foo

val value: Int = 0
