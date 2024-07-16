public inline fun <T, R> with(receiver: T, f: T.() -> R): R = receiver.f()

class A(val a: Int) {
    fun A.f<caret>oo(): Int {
        return 42
    }

    fun test() {
        A(1).foo()
    }
}

fun test() {
    val t = with(A(1)) {
        A(2).foo()
    }
}