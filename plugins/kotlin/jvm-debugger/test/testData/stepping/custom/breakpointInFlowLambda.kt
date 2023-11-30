// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4)
package breakpointInFlowLambda


import kotlinx.coroutines.flow.*

fun foo1() = "1"
fun foo2() = "2"

suspend fun main() {
  testOneLambda()
  testSeveralLambdas()
  testSeveralCallsOfOneMethod()
}



private suspend fun testOneLambda() {
  flowOf(1, 2)
    // STEP_INTO: 1
    // RESUME: 1
    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 1
    .map { foo1() }
    .toList()
}

private suspend fun testSeveralLambdas() {
  flowOf(1, 2)
    // STEP_INTO: 1
    // RESUME: 1
    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 1
    .map { foo1() }.filter { it.length > 3 }
    .toList()
}

private suspend fun testSeveralCallsOfOneMethod() {
  flowOf(1, 2)
    // STEP_INTO: 1
    // RESUME: 1
    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 1
    .map { foo1() }.map { foo2() }
    .toList()
}
