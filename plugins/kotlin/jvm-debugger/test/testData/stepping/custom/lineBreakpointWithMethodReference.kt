package lineBreakpointWithMethodReference

import lineBreakpointWithMethodReference.next
import lineBreakpointWithMethodReference.next as nextAlias

fun testLineBreakpointWithLambda() {
    val list = listOf(1, 2, 3)
    //Breakpoint!, lambdaOrdinal = -1
    list.flatMap { it.digits() }
}

fun Int.digits(): List<Int> = toString().map { it - '0' }

fun testLineBreakpointWithMethodReference() {
    val list = listOf(1, 2, 3)
    //Breakpoint!, lambdaOrdinal = -1
    list.stream().map(Int::digits).toList()
}

fun testLineBreakpointWithInlineMethodReference() {
    val list = listOf(1, 2, 3)
    //Breakpoint!, lambdaOrdinal = -1
    list.map(Int::digits)
}

fun main() {
    testLineBreakpointWithLambda()
    testLineBreakpointWithMethodReference()
    testLineBreakpointWithInlineMethodReference()
    testLineBreakpointWithRepeatedMethodReferences()
}

@JvmName("renamedMethodReferences")
fun testLineBreakpointWithRepeatedMethodReferences() {
    val list = listOf(1, 2, 3)
    //Breakpoint!, lambdaOrdinal = -1
    list.stream().map(Int::next).map(Int::digits).map(List<Int>::first).map { it + 1 }.map(Int::next).toList()

    fun local() {
        //Breakpoint!, lambdaOrdinal = -1
        list.stream().map(Int::next).toList()
    }
    local()

    //Breakpoint!, lambdaOrdinal = -1
    list.stream().map(Int::nextAlias).toList()
}

@JvmName("renamedNext")
fun Int.next(): Int = this + 1

// RESUME: 20
