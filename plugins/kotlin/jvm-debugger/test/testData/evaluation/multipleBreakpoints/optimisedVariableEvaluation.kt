package optimisedVariables

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2)

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

suspend fun foo() {
    val a = 1
    // EXPRESSION: a
    // RESULT: 1: I
    //Breakpoint!
    println("")
    use(a)
    yield()
    // EXPRESSION: a
    // RESULT: This variable is inaccessible because it isn't used after the last suspension point
    //Breakpoint!
    println("")
}

fun main() = runBlocking {
    val a = 1
    // EXPRESSION: a
    // RESULT: 1: I
    //Breakpoint!
    println("")
    use(a)
    yield()
    // EXPRESSION: a
    // RESULT: This variable is inaccessible because it isn't used after the last suspension point
    //Breakpoint!
    println("")
    foo()
}

fun use(value: Any) {

}
