// INLINE_METHOD_THIS: true

operator fun String.unaryMinus(): String {
    println(this)
    return this
}

fun calculate(value: String): String = -value

fun use(value: String) {
    calcu<caret>late(value)
}
