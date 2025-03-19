package ceLocalClassMembers

fun main(args: Array<String>) {
    A().test()
}

class A {
    public fun publicFun(): Int = 1
    public val publicVal: Int = 2

    protected fun protectedFun(): Int = 3
    protected val protectedVal: Int = 4

    @JvmField
    protected val protectedField: Int = 5

    private fun privateFun() = 6
    private val privateVal = 7

    fun test() {
        //Breakpoint!
        val a = 1
    }
}
