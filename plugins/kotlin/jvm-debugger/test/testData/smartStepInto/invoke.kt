fun foo() {
    val a = A()
    a()<caret>
}

class A {
    operator fun invoke() {}
}

// EXISTS: invoke()
