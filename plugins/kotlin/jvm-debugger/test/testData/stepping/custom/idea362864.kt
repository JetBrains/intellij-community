// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4)
package kt.example

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

class A {
    suspend fun foo() = 10
}
fun createA() = A()
fun consume(a: A): A = a

suspend fun funCallWithYield(x: A) = with(consume(x)) {
    yield()
    foo()
}

suspend fun testWithYield() {
    // SMART_STEP_INTO_BY_INDEX: 1
    // RESUME: 1
    //Breakpoint!
    funCallWithYield(createA())
}

suspend fun funCallWithoutYield(x: A) = with(consume(x)) {
    foo()
}

suspend fun testWithoutYield() {
    // SMART_STEP_INTO_BY_INDEX: 1
    // RESUME: 1
    //Breakpoint!
    funCallWithoutYield(createA())
}

suspend fun funCallSeparateLine(x: A) =
    with(consume(x)) {
        foo()
    }

suspend fun testSeparateLine() {
    // SMART_STEP_INTO_BY_INDEX: 1
    // RESUME: 1
    //Breakpoint!
    funCallSeparateLine(createA())
}

suspend fun funCallSeparateLineWithYield(x: A) =
    with(consume(x)) {
        yield()
        foo()
    }

suspend fun testSeparateLineWithYield() {
    // SMART_STEP_INTO_BY_INDEX: 1
    // RESUME: 1
    //Breakpoint!
    funCallSeparateLineWithYield(createA())
}

fun main() = runBlocking {
    testWithYield()
    testWithoutYield()
    testSeparateLine()
    testSeparateLineWithYield()
}
