// BIND_TO test.barFoo
package test

fun foo() {
    val x: Any? = null
    x?.<caret>fooBar
}

val Any?.fooBar: Any? = null

val Any?.barFoo: Any? = null