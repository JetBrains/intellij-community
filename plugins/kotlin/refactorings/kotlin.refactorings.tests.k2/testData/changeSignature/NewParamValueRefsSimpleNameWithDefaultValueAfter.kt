class A {
    val prop = ""

    fun foo() = bar(prop)

    private fun bar(p2: Int): Boolean {
        return true
    }
}