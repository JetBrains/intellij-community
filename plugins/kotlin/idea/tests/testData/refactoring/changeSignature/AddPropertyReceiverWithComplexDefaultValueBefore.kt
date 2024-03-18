package usages

val <caret>foo: String get() = "hello"

fun callFoo() = foo
fun t() {
    val s = foo + " world"
}