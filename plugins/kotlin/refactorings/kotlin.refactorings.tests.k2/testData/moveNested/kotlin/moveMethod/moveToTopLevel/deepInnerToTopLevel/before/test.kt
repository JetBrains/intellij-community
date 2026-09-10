package bar

class Test {
    val a: Int = 5
    inner class Test2 {
        inner class Test3 {
            val b: Int = 5
            fun foo<caret>(): Int {
                println(this@Test2)
                return a + b
            }
        }
    }
}