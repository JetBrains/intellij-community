// INLINE_METHOD_THIS: true

interface A {
    operator fun get(index: Int): Int
}

fun calculate(a: A): Int = a[0]

fun use(a: A) {
    calcu<caret>late(a)
}
