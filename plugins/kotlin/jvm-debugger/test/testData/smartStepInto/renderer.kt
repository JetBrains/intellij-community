fun test() {
    <caret>propFoo + foo() + fooWithParam(1.extFoo()) { 2 } + FooClass(1).test() + FooClass().test() + FooClass(1, 2).test()
}

class FooClass(i: Int) {
    constructor() : this(1)
    constructor(i: Int, s: Int) : this(1)

    fun test() = 1
}

fun foo() = 1
fun fooWithParam(i: Int, f: () -> Int) = 1
fun Int.extFoo() = 1

val propFoo: Int
    get() {
        return 1
    }

// EXISTS: propFoo.get()
// EXISTS: foo()
// EXISTS: fooWithParam(Int\, () -> Int)
// EXISTS: extFoo()
// EXISTS: fooWithParam: f.invoke()
// EXISTS: constructor FooClass(Int)
// EXISTS: test()_0
// EXISTS: constructor FooClass()
// EXISTS: test()_1
// EXISTS: constructor FooClass(Int\, Int)
// EXISTS: test()_2
