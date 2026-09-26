class Test {
    fun notNullString(p: Int): String {
        return "response"
    }

    private val notNullInitializerField: String = notNullString(3)
    var notNullInitializerPublicField: String = notNullString(3)

    fun testProperty() {
        notNullInitializerField.get(0)
        notNullInitializerPublicField.get(0)
    }

    fun testLocalVariable() {
        val notNullInitializerVal = notNullString(3)
        notNullInitializerVal.get(0)
    }
}
