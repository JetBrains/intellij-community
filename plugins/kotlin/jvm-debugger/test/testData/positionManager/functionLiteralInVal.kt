class A {
    fun foo() {
        val a = {
            fun innerFoo() {
                val b = 1   // A
            }
            innerFoo()
        }()
    }
}
