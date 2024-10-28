class A {
    fun foo() {
        {
            fun innerFoo() {
                ""   // A
            }
            innerFoo()
        }()
    }
}
