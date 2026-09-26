class C {
    fun foo(s: String? = null, b: Boolean? = null, i: Int = 42) {}
}

fun main() {
    C().foo(null, false)
}
