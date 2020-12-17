package suspendFunctionsWithoutKotlinxCoroutines

suspend fun one() = 1

fun main() {
    // EXPRESSION: one()
    // RESULT: Failed to wrap suspend function in kotlinx.coroutines.runBlocking, check your classpath for kotlinx.coroutines package
    //Breakpoint!
    println("")
}
