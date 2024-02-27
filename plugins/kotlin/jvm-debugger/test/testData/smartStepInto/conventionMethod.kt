class A {
    operator fun plus(a: A) = a
}

fun foo() {
    f1() + A() + A()<caret>
}

fun f1() = A()

// EXISTS: f1(), plus(A), plus(A)
