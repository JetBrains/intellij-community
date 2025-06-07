package test1

class Test {
    inner class Inner<caret> {
        fun foo() {
            println(this@Test)
        }
    }
}