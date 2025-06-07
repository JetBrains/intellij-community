package bar

class Test {
    val a: Int = 5
    fun foo<caret>(b: Int): Int {
        return a + b
    }
}

fun outside(test: Test): Int {
    return test.foo(5)
}