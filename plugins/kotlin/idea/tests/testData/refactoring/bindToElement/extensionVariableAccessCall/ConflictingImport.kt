// FILE: test/ConflictingImport.kt
// BIND_TO test.bar.fooBar
package test

// There should be 2 imports here, import optimizer incorrectly removes the second import see KTIJ-28933
import test.foo.fooBar

fun foo() {
    0.<caret>fooBar()
}

// FILE: test/foo/A.kt
package test.foo

val fooBar: Any.() -> Unit = { }

// FILE: test/bar/A.kt
package test.bar

val fooBar: Any.() -> Unit = { }