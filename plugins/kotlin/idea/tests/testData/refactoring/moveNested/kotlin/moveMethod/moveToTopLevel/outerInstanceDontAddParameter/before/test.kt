package bar

class Test {
    val a: Int
    fun foo<caret>(): Int {
        return a + a
    }
}