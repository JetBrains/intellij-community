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

// Working as intended on EE-IR: No support for disabling reflective access

// REFLECTION_PATCHING: false

// EXPRESSION: intBlock { publicFun() }
// RESULT: 1: I

// EXPRESSION: intBlock { publicVal }
// RESULT: Method threw 'java.lang.IllegalAccessError' exception.

// EXPRESSION: intBlock { protectedFun() }
// RESULT: Method threw 'java.lang.IllegalAccessError' exception.

// EXPRESSION: intBlock { protectedVal }
// RESULT: Method threw 'java.lang.IllegalAccessError' exception.

// EXPRESSION: intBlock { protectedField }
// RESULT: Method threw 'java.lang.IllegalAccessError' exception.

// EXPRESSION: intBlock { privateFun() }
// RESULT: Method threw 'java.lang.VerifyError' exception.

// EXPRESSION: intBlock { privateVal }
// RESULT: Method threw 'java.lang.IllegalAccessError' exception.
