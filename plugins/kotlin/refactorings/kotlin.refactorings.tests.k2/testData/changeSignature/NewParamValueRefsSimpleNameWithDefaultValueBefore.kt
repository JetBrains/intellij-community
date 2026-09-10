class A {
    val prop = ""

    fun foo() = bar("")

    private fun b<caret>ar(x: String): Boolean {
        return true
    }
}