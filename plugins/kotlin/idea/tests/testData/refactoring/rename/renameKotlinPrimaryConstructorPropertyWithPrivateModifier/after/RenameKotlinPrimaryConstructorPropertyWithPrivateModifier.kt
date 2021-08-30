class C(private val bar: Int) {
    fun f() = bar
}

fun test() {
    C(bar = 1)
}