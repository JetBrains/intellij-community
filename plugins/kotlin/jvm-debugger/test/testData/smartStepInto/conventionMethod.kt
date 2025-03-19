class A {
    operator fun plus(a: A) = a
}

fun foo() {
    f1() + A() + A()<caret>
}

fun f1() = A()

// EXISTS: f1(), plus(A)_0, constructor A()_0, plus(A)_1, constructor A()_1
