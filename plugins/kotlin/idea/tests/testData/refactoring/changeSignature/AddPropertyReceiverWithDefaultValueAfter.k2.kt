package usages

import a.b.c.Dep.Companion.MY_CONSTANT_FROM_DEP

val Int.foo: String get() = "hello"

fun callFoo() = MY_CONSTANT_FROM_DEP.foo
fun t() {
    val s = MY_CONSTANT_FROM_DEP.foo + " world"
}
