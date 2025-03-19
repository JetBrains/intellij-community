internal class J {
    private fun test() {
        val message = B.vararg(
            "first",
            "second"
        )
    }
}

internal object B {
    fun vararg(key: String?, vararg params: Any?): String {
        return ""
    }
}
