fun localFun() { /// M
    fun local() { /// L, M
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
    fun local(block: () -> Unit = { println() }) {} /// *, L, M, λ
} /// L

fun localClass() { /// M
    class Local { /// M
        fun foo() { /// L, M
            println() /// L
        } /// L
    }
    Local().foo() /// L
} /// L
