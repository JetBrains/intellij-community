package bar

class Test {
    val a: Int = 5
    fun foo<caret>(b: Int): Int {
        return a + b
    }
}

fun Test.outside(): Int {
    return foo(5)
}