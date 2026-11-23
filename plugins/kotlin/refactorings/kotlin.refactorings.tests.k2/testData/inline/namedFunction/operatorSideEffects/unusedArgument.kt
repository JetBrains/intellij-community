// INLINE_METHOD_THIS: true

class A {
    operator fun unaryMinus(): Int {
        println("unaryMinus")
        return 1
    }
}

fun ignore(value: Int) {}

fun use(a: A) {
    ign<caret>ore(-a)
}
