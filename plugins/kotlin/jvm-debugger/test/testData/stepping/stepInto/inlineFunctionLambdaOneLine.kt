// FILE: inlineFunctionLambdaOneLine.kt
package inlineFunctionLambdaOneLine

fun main(args: Array<String>) {
    //Breakpoint! (lambdaOrdinal = -1)
    42.let { 1 + it }
}

// STEP_INTO: 2
