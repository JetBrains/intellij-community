package bar

class Test {
    val a: Int = 5
    fun foo<caret>(): Int {
        return a + a
    }
}