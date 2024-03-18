fun localFun() { /// M
    // We don't support function breakpoints for local functions yet
    fun local() { /// L
        println() /// L
    } /// L
} /// L

fun functionalLiteral() { /// M
    val local = fun() { /// L
        println() /// L
    } /// L
} /// L

fun functionalLiteralOneLiner() { /// M
    val local = fun() { println() } /// *, L, λ
} /// L

fun functionalLiteralExpression() { /// M
    val local = fun() = /// L
        println() /// L
} /// L

fun functionalLiteralOneLinerExpression() { /// M
    val local = fun() = println()  /// *, L, λ
} /// L

fun lambdaLiteral() { /// M
    val local = { /// L
        println() /// L
    } /// L
} /// L

fun lambdaLiteralOneLine() { /// M
    val local = { println() } /// *, L, λ
} /// L

fun defaultArgumentLambda() { /// M
    fun local(block: () -> Unit = { println() }) {} /// *, L, λ
} /// L
