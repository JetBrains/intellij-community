package javaSyntheticPropertyGetter

fun main(args: Array<String>) {
    //Breakpoint!
    Foo().foo = foo(bar(1))
}

fun foo(any: Any) = any
fun bar(any: Any) = any

class Foo() {
    var foo: Any = 2
        set(value) {
            field = value
        }
}

// SMART_STEP_INTO_BY_INDEX: 2
// IGNORE_K2
