// INLINE_METHOD_THIS: true

class A {
    operator fun unaryMinus(): Int {
        println("unaryMinus")
        return 1
    }
}

fun duplicate(value: Int): Int = value + value

fun use(a: A) {
    println(dupli<caret>cate(-a))
}
