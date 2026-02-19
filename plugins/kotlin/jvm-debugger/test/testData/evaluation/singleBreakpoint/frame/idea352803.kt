package idea352803

fun <R> applyLambda(action: () -> R) = action()

inline fun <R> inlineWithCrossLambda(crossinline action: Any.() -> R) =
    applyLambda { Obj.inlineInClassWithLambda(action) }

object Obj {
    inline fun <R> inlineInClassWithLambda(action: Any.() -> R) = Any().action()
}

fun main() {
    inlineWithCrossLambda {
        //Breakpoint!
        "hello"
    }
}

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES
