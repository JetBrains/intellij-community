package usages

import a.b.c.Dep

fun Int.foo<caret>() {}

fun callFoo() = Dep.MY_CONSTANT_FROM_DEP.foo()
