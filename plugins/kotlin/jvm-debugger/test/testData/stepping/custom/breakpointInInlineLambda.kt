package breakpointInInlineLambda

fun nullOrNot(): Any? {
    return Any()
}

inline fun inlineFoo(f: () -> Unit) {
    nullOrNot() ?: f()
    return f()
}

fun foo() = inlineFoo {
    //Breakpoint!
    println()
}

fun main() {
    foo()
}
