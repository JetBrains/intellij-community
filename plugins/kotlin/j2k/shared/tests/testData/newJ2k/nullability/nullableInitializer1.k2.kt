class Test {
    fun nullableString(p: Int): String? {
        return if (p > 0) "response" else null
    }

    private var nullableInitializerField: String? = nullableString(3)
    private val nullableInitializerFieldFinal: String? = nullableString(3)
    var nullableInitializerPublicField: String? = nullableString(3)

    fun testProperty() {
        nullableInitializerField = "aaa"

        nullableInitializerField!!.get(0)
        nullableInitializerFieldFinal!!.get(0)
        nullableInitializerPublicField!!.get(0)
    }

    fun testLocalVariable() {
        val nullableInitializerVal = nullableString(3)
        nullableInitializerVal!!.get(0)
    }
}
