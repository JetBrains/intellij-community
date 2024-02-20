fun foo() {
    val a = A()
    a.f1(f2())<caret>
}

class A {
    fun f1(x: Int): Int = 1
}

fun f2(): Int = 2

// EXISTS: f1(Int), f2()
