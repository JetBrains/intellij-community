class A(val k: Int) {
    fun X.<caret>foo(s: String, n: Int): Boolean {
        return s.length - n.inc() + this@A.k > 0
    }

    fun test() {
        X(0).foo("1", 2)
    }
}

class X(val k: Int)

fun test() {
    with(A(3)) {
        X(0).foo("1", 2)
    }
}