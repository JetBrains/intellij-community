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
    // RESULT: 1: I
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
    // RESULT: 1: I
    //Breakpoint!
    println("")
    foo()
}

fun use(value: Any) {

}
