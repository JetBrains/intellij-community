class TestJava {
    fun nullableObj(p: Int): Any? {
        return if (p > 0) "response" else null
    }

    var nullableInitializerFieldCast: String? = nullableObj(3) as String?
    private val nullableInitializerPrivateFieldCast = nullableObj(3) as String?

    fun testProperty() {
        nullableInitializerFieldCast!!.get(0)
        nullableInitializerPrivateFieldCast!!.get(0)
    }

    fun testLocalVariable() {
        val nullableInitializerValCast = nullableObj(3) as String?

        nullableInitializerValCast!!.get(0)
    }
}
