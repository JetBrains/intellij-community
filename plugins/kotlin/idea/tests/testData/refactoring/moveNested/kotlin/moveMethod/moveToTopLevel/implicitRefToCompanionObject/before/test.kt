package bar

class Test {
    companion object {
        val a: Int = 5
        fun foo<caret>(): Int {
            // TODO: The .Companion parts here can be removed after KT-64842 is fixed
            return a + a
        }
    }
}