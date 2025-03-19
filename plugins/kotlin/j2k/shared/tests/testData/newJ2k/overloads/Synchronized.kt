internal class A {
    fun foo(p: Int) {
        println(p)
    }

    @Synchronized
    fun foo() {
        foo(calcSomething())
    }

    // this method should be invoked under synchronized block!
    private fun calcSomething(): Int {
        return 0
    }
}
