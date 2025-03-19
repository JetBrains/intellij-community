// One-liners should have a method breakpoint and a line breakpoint
fun foo1() = println() /// L, M

fun foo2() {} /// M

// Lambdas should be available if present
fun foo3() = run { println() } /// *, L, M, λ

// No need to suggest lambda breakpoint for multiline lambdas
fun foo3_1() = run { /// L, M
    println() /// L
} /// L

// However, we need to suggest lambda breakpoint when there is some code on the line inside the lambda
fun foo3_2() = run { println() /// *, L, M, λ
    println() /// L
} /// L

fun foo3_3() = run { run { println() } /// *, L, M, λ, λ
    println() /// L
} /// L

fun foo3_4() = run { /* comment */ println() /// *, L, M, λ
    println() /// L
} /// L

fun foo3_5() = run { /* comment */ /// L, M
    println() /// L
} /// L

// Code blocks {} are not considered as expressions
fun foo4() { /// M
    println() /// L
} /// L

// And parenthesis as well
fun foo5() = ( /// M
        println() /// L
        )

// For expression-body functions, a line breakpoint should be available
// if there is an expression on the first line
fun foo6() = when (2 + 3) { /// M, L
    5 -> {} /// L
    else -> {} /// L
}

// Line breakpoint should not be displayed for lambda literal results
fun foo7() = { println() } /// M, λ

fun foo7_1() = { /// M
    println() /// L
} /// L

fun foo7_2() = /// M
    { println() } /// λ

fun foo8() = (3 + 5).run { /// L, M
    println() /// L
} /// L

// Expressions in default parameter values should be recognized
fun foo9(a: String = readLine()!!) = a /// M, L

// Lambdas in default parameter values also should be recognized
fun foo10(a: () -> Unit = { println() }) { /// *, L, M, λ
    a() /// L
} /// L

// If a default parameter value is not just a lambda, but a function call with a lambda argument,
// there should be a line breakpoint as well
fun foo11(a: String = run { "foo" }) = a /// *, L, M, λ

// Lambda breakpoints should be accessible on lines where the lambda expression starts
fun foo12() { /// M
    listOf(1, 2, 3, 4, 5) /// L
        .filter { it % 2 == 0 } /// *, L, λ
        .map { /// L
            it * 2 /// L
        } /// L
        .joinToString() /// L
} /// L

fun foo13() { /// M
    listOf(1, 2, 3, 4, 5) /// L
      .map { it * 2 }.map { it * 3 } /// *, L, λ, λ
} /// L

fun foo14() { /// M
    listOf(1, 2, 3, 4, 5) /// L
      .map { x -> x.let { 42 } } /// *, L, λ, λ
} /// L

fun foo3_6() = run { /// L, M
    println(1) }.also { println(2) } /// *, L, λ

fun foo15_empty_lambda() = run {} /// *, L, M, λ
fun foo16_almost_empty_lambda() = run { /* boo! */ } /// *, L, M, λ
