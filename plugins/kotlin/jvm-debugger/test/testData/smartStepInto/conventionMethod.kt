class A {
    operator fun plus(a: A) = a
}

fun foo() {
    f1() + A() + A()<caret>
}

fun f1() = A()

// EXISTS: plus(A), f1()
