// ERROR: INVISIBLE_REFERENCE
class J {
    private val s = S()

    fun test() {
        s.prop = ""
    }

    internal class S {
        private var prop: String? = null
    }
}
