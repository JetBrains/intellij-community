// ERROR: Cannot access 'var prop: String?': it is private in 'J.S'.
class J {
    private val s = S()

    fun test() {
        s.prop = ""
    }

    internal class S {
        private var prop: String? = null
    }
}
