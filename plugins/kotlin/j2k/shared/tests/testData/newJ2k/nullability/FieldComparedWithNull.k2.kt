internal class C {
    private val s: String? = x()

    private fun x(): String? {
        return null
    }

    fun foo() {
        if (s == null) {
            print("null")
        }
    }
}
