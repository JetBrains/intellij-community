package bar

class Test {
    val a: Int = 5
    inner class Test2 {
        inner class Test3 {
            val b: Int = 5
            fun Test2.foo<caret>(): Int {
                println(this)
                with(Test2()) {
                    println(this)
                }
                return a + b
            }
        }
    }
}