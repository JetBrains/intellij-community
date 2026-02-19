package ceMembers

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

fun <T> block(block: () -> T): T {
    return block()
}

// EXPRESSION: block { publicFun() }
// RESULT: 1: I

// EXPRESSION: block { publicVal }
// RESULT: 2: I

// EXPRESSION: block { protectedFun() }
// RESULT: 3: I

// EXPRESSION: block { protectedVal }
// RESULT: 4: I

// EXPRESSION: block { protectedField }
// RESULT: 5: I

// EXPRESSION: block { privateFun() }
// RESULT: 6: I

// EXPRESSION: block { privateVal }
// RESULT: 7: I
