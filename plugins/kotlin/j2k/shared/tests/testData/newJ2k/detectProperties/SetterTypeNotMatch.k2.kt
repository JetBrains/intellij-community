internal class A {
    var value: Any? = null
        private set

    fun setValue(s: String?) {
        takesString(s)
        this.value = s
    }

    private fun takesString(s: String?) {}
}
