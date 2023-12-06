internal class A {
    var value: Any? = null
        private set

    fun setValue(s: String) {
        takesString(s)
        value = s
    }

    private fun takesString(s: String) {}
}
