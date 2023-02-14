// "Replace with safe (?.) call" "true"
// WITH_STDLIB
class Foo(val bar: Bar)

class Bar {
    operator fun invoke() {}
}

fun test(foo: Foo?) {
    foo // comment1
        ?. /* comment2 */ bar<caret>()
}
/* IGNORE_FIR */