package bar

class Test {
    companion object {
        val a: Int = 5
        fun foo<caret>(): Int {
            return a + a
        }
    }
}