package b

import a.Foo

fun foo(f: Foo) {
    val bar = f.bar
    val (_, y) = bar
}