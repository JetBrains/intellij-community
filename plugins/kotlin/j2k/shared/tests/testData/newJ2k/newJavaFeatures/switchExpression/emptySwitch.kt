// ERROR: Initializer type mismatch: expected 'Int', actual 'Unit'.
// ERROR: 'when' expression must be exhaustive. Add an 'else' branch.
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
