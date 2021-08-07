package usages

import a.b.c.Dep

fun foo<caret>(i: Int) {}

fun callFoo() = foo(Dep.MY_CONSTANT_FROM_DEP)
