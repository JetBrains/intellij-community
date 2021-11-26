package whenEvaluation

fun main(args: Array<String>) {
    val a = "x"
    //Breakpoint!
    args.size
}

// EXPRESSION: when (a) { "a" -> "A"; "b" -> "B"; else -> "C" }
// RESULT: "C": Ljava/lang/String;

// TODO: Muted on the IR backend
// An interaction between the fragment compilation strategy of wrapping
// the fragment in a block and the front end analysis of what expressions'
// values are used causes code generation to discard the result of the when.
