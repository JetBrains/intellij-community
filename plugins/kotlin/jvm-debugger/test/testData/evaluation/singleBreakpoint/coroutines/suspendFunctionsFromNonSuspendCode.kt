// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2)
package suspendFunctionsWithoutKotlinxCoroutines

suspend fun one() = 1

fun main() {
    // EXPRESSION: one()
    // RESULT: 1: I
    //Breakpoint!
    println("")
}
