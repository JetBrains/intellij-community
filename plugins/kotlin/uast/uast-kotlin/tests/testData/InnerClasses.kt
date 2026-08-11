class Foo(val d: Int) {
    class Bar(val a: Int, val b: Int) {
        fun getAPlusB() = a + b
        class Baz {
            fun doNothing() = Unit
        }
    }

    inner class Boo(val c: Int) {
        fun getCPlusD() = c + d
    }
}
