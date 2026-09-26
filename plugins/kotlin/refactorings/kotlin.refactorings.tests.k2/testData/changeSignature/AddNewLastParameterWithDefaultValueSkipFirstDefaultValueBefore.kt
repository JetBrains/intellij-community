class C {
    fun f<caret>oo(s: String? = null, b: Boolean? = null) {}
}

fun main() {
    C().foo(null, false)
}