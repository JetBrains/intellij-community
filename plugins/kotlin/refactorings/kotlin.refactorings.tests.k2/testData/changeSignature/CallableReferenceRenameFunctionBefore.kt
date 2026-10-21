fun <caret>foo(n: Int): Int = n

fun test() {
    val f: (Int) -> Int = ::foo
    f(1)
}