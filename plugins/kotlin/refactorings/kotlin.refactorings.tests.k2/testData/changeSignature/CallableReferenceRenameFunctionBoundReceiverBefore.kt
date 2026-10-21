class A {
    fun <caret>foo(n: Int): Int = n
}

fun test(a: A) {
    val f: (Int) -> Int = a::foo
    f(1)
}