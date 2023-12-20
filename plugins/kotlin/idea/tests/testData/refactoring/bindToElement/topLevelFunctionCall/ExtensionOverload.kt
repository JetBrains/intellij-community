// FILE: test/Test.kt
// BIND_TO bar.fooBar
package test

fun Any.usage() {
    foo<caret>Bar() //shorten references bug https://youtrack.jetbrains.com/issue/KT-64493, should be bar.fooBar()
}

// FILE: bar/Bar.kt
package bar

fun fooBar() {}

fun Any.fooBar() {}
