// PARAM_DESCRIPTOR: val s: kotlin.String defined in foo
// PARAM_TYPES: kotlin.String, kotlin.Comparable<kotlin.String>, kotlin.CharSequence, java.io.Serializable, kotlin.Any
// WITH_STDLIB

import org.jetbrains.annotations.NotNull

class Foo {
    fun foo(): @NotNull String = ""
}

fun foo(foo: Foo) {
    val s = foo.foo()
    <selection>println(s)</selection>
}