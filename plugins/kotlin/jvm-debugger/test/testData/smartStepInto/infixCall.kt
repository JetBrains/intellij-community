fun foo() {
    val a = A()
    f2(a f1 1)<caret>
}

class A {
    infix fun f1(i: Int) = 1
}

fun f2(i: Int) {}

// EXISTS: f2(Int), f1(Int)
