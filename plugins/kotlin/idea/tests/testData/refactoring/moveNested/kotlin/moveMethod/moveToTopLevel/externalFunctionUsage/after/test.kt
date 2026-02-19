package bar

class Test {
    val a: Int = 5
}

fun outside(test: Test): Int {
    return foo(test, 5)
}