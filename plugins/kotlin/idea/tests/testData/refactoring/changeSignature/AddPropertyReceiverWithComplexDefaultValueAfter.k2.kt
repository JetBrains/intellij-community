package usages

import a.b.Dep2
import a.b.c.Dep

val Int.foo: String get() = "hello"

fun callFoo() = Dep2().eval(Dep.Companion.MY_CONSTANT_FROM_DEP + Dep2.Companion.NUMBER).foo
fun t() {
    val s = Dep2().eval(Dep.Companion.MY_CONSTANT_FROM_DEP + Dep2.Companion.NUMBER).foo + " world"
}
