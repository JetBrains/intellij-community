// ERROR: Initializer type mismatch: expected 'kotlin.Int', actual 'kotlin.Unit'.
// ERROR: 'when' expression must be exhaustive. Add an 'else' branch.
// ERROR: New inference error [NewConstraintError at Incorporate TypeVariable(R) == kotlin/Unit from Fix variable R from position Fix variable R: kotlin/Unit <!: kotlin/Int].
// ERROR: Argument type mismatch: actual type is 'kotlin.Unit', but 'kotlin.Int' was expected.
// ERROR: Type mismatch: inferred type is 'kotlin.Unit', but 'kotlin.Int' was expected.
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
