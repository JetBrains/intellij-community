package ceSuperAccess

fun main(args: Array<String>) {
    A().Inner().test()
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

    inner class Inner {
        fun test() {
            //Breakpoint!
            val a = publicFun()
        }
    }
}

fun <T> intBlock(block: () -> T): T {
    return block()
}

// EXPRESSION: intBlock { publicFun() }
// RESULT: 1: I

// EXPRESSION: intBlock { publicVal }
// RESULT: 2: I

// EXPRESSION: intBlock { protectedFun() }
// RESULT: 3: I

// EXPRESSION: intBlock { protectedVal }
// RESULT: 4: I

// EXPRESSION: intBlock { protectedField }
// RESULT: 5: I

// EXPRESSION: intBlock { privateFun() }
// RESULT: 6: I

// EXPRESSION: intBlock { privateVal }
// RESULT: 7: I
