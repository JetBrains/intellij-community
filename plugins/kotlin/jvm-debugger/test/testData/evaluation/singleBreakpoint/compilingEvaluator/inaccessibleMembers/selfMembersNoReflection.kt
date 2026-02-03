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

// Working as intended on EE-IR: No support for disabling reflective access

// REFLECTION_PATCHING: false

// EXPRESSION: block { publicFun() }
// RESULT: 1: I

// EXPRESSION: block { publicVal }
// RESULT: Method threw 'java.lang.IllegalAccessError' exception.

// EXPRESSION: block { protectedFun() }
// RESULT: Method threw 'java.lang.IllegalAccessError' exception.

// EXPRESSION: block { protectedVal }
// RESULT: Method threw 'java.lang.IllegalAccessError' exception.

// EXPRESSION: block { protectedField }
// RESULT: Method threw 'java.lang.IllegalAccessError' exception.

// EXPRESSION: block { privateFun() }
// RESULT: Method threw 'java.lang.VerifyError' exception.

// EXPRESSION: block { privateVal }
// RESULT: Method threw 'java.lang.IllegalAccessError' exception.