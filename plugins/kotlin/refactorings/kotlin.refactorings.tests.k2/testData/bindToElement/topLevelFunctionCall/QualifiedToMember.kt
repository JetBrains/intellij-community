// FILE: test/Qualified.kt
// BIND_TO test.Container.fooBar
package test

class Container {
    fun fooBar() { }
}

fun caller() {
    test.foo.<caret>fooBar()
}

// FILE: test/foo/Test.kt
package test.foo

fun fooBar() { }
