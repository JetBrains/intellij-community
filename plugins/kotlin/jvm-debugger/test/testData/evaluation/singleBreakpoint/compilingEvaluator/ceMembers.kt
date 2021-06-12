package ceMembers

fun main(args: Array<String>) {
    A().test()
}

class A {
    public fun publicFun(): Int = 1
    public val publicVal: Int = 1

    protected fun protectedFun(): Int = 1
    protected val protectedVal: Int = 1

    private fun privateFun() = 1
    private val privateVal = 1

    fun test() {
        //Breakpoint!
        val a = 1
    }
}

fun foo(p: () -> Int) = p()

// EXPRESSION: foo { publicFun() }
// RESULT: 1: I

// EXPRESSION: foo { publicVal }
// RESULT: Method threw 'java.lang.IllegalAccessError' exception.

// EXPRESSION: foo { protectedFun() }
// RESULT: Method threw 'java.lang.IllegalAccessError' exception.

// EXPRESSION: foo { protectedVal }
// RESULT: Method threw 'java.lang.IllegalAccessError' exception.

// EXPRESSION: foo { privateFun() }
// RESULT: Method threw 'java.lang.VerifyError' exception.

// EXPRESSION: foo { privateVal }
// RESULT: Method threw 'java.lang.IllegalAccessError' exception.

