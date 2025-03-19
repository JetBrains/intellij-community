class A(val n: Int)

fun A.f<caret>oo(): Int {
    return 42
}

fun test() {
    A(1).foo()
    with(A(1)) {
        foo()
    }
}