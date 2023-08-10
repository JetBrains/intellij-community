package privatePropertyWithExplicitDefaultGetter

fun main(args: Array<String>) {
    val base = Some()

    //Breakpoint!
    args.size
}

class Some {
    private val a: Int = 1
        get() = 4 * field
}

// EXPRESSION: base.a
// RESULT: 4: I
