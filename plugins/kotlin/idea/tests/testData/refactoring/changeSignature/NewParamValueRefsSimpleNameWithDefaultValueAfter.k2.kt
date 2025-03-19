class A {
    val prop = ""

    fun foo() = bar(this@A.prop)

    private fun bar(p2: Int): Boolean {
        return true
    }
}