// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.2.jar)
package suspendFunctionsWithoutKotlinxCoroutines

suspend fun one() = 1

fun main() {
    // EXPRESSION: one()
    // RESULT: 1: I
    //Breakpoint!
    println("")
}
