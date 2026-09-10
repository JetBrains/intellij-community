class A(val n: Int) {
    fun fo<caret>o(): Int {
        return i
    }
}

fun test() {
    A(1).foo()
    with(A(1)) {
        foo()
    }
}