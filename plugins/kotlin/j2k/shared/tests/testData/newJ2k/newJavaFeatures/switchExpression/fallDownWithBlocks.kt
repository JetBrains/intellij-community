// ERROR: Initializer type mismatch: expected 'Int', actual 'Unit'.
// ERROR: 'when' expression must be exhaustive. Add an 'else' branch.
// ERROR: Return type mismatch: expected 'Int', actual 'Unit'.
// ERROR: Type mismatch: inferred type is 'Unit', but 'Int' was expected.
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
