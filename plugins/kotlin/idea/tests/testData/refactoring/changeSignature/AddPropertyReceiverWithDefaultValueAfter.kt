package usages

import a.b.c.Dep

val Int.foo: String get() = "hello"

fun callFoo() = Dep.MY_CONSTANT_FROM_DEP.foo
fun t() {
    val s = Dep.MY_CONSTANT_FROM_DEP.foo + " world"
}
