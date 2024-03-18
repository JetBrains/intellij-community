package usages

fun foo(i: Int) {}

fun callFoo() = foo(a.b.c.Dep.Companion.MY_CONSTANT_FROM_DEP)
