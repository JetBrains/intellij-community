// ERROR: Type mismatch: inferred type is Unit but Int was expected
// ERROR: Type mismatch: inferred type is Unit but Int was expected
// ERROR: 'when' expression must be exhaustive, add necessary 'else' branch
object C {
    @JvmStatic
    fun main(args: Array<String>) {
        val a: Int = when (args.size) {
            1 -> {
                run {
                    val a = 1
                    print("1")
                }
                run {
                    val a = 2
                    print("2")
                }
            }

            2 -> {
                val a = 2
                print("2")
            }
        }
    }
}
