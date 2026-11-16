// ERROR: INITIALIZER_TYPE_MISMATCH
// ERROR: NO_ELSE_IN_WHEN
object NonDefault {
    @JvmStatic
    fun main(args: Array<String>) {
        val value = 3
        val valueString = ""
        val a: Int = when (value) {
        }
        println(valueString)
    }
}
