// "Replace with safe (?.) call" "true"
// WITH_RUNTIME
class Foo(val bar: Bar)

class Bar {
    operator fun invoke() {}
}

fun test(foo: Foo?) {
    foo // comment1
        ?. /* comment2 */ bar<caret>()
}
/* IGNORE_FIR */