// ERROR: Type mismatch: inferred type is Unit but Int was expected
// ERROR: 'when' expression must be exhaustive, add necessary 'else' branch
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
