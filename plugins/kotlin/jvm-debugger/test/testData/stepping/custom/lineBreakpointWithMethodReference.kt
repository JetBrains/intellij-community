package lineBreakpointWithMethodReference

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
}

// RESUME: 20
