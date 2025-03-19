fun test() {
    <caret>foo(foo(foo(foo(1))))
}

fun foo(x: Int) = x

// EXISTS: foo(Int)_3, foo(Int)_2, foo(Int)_1, foo(Int)_0
