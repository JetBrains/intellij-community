package usages

import a.b.Dep2.Companion.NUMBER
import a.b.c.Dep.Companion.MY_CONSTANT_FROM_DEP

val Int.foo: String get() = "hello"

fun callFoo() = Dep2().eval(MY_CONSTANT_FROM_DEP + NUMBER).foo
fun t() {
    val s = Dep2().eval(MY_CONSTANT_FROM_DEP + NUMBER).foo + " world"
}
