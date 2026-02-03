class J {
    private val s = S()

    fun test() {
        s.prop = ""
    }

    internal class S {
        internal var prop: String? = null
    }
}
