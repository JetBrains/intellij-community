// KTIJ-39470: a not-null annotation on a field must remove the `?` from the property type
package test

class Test(private val initializedInConstructor: String) {
    private lateinit var notNullField: String

    private val notNullBoxedField: Int? = null

    private val nullableField: String? = null

    private val plainField: String? = null

    fun use() {
        println(notNullField.length)
        println(notNullBoxedField.toString() + nullableField + plainField)
    }
}
