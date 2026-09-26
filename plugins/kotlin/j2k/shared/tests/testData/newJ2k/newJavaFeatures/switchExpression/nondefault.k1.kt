// ERROR: 'when' expression must be exhaustive, add necessary 'else' branch
object NonDefault {
    @JvmStatic
    fun main(args: Array<String>) {
        val value = 3
        var valueString = ""
        val a = when (value) {
            1 -> {
                valueString = "ONE"
                1
            }

            2 -> {
                valueString = "TWO"
                2
            }

            3 -> {
                valueString = "THREE"
                3
            }
        }
        println(valueString)
    }
}
