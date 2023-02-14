// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB
class Foo(val bar: Bar?)

class Bar(val baz: Baz)

class Baz {
    operator fun invoke() {}
}

fun test(foo: Foo) {
    foo.bar?.baz<caret>()
}
/* IGNORE_FIR */