package localFun

fun main(args: Array<String>) {
    fun myLocalFun1() = 1

    // EXPRESSION: myLocalFun1()
    // RESULT: 1: I
    //Breakpoint!
    myLocalFun1()

    fun myLocalFun2() = 2
    // EXPRESSION: myLocalFun2()
    // RESULT: 2: I
    //Breakpoint!
    myLocalFun2()

    fun myLocalFun3() {
        // EXPRESSION: myLocalFun1() + 1
        // RESULT: Cannot find local variable 'myLocalFun1' with type kotlin.jvm.functions.Function0
        //Breakpoint!
        myLocalFun1() + 1
    }

    myLocalFun3()


    fun myLocalFun4(): Int {
        return 1
    }

    // EXPRESSION: myLocalFun4()
    // RESULT: 1: I
    //Breakpoint!
    myLocalFun4()

    fun myLocalFun5(i: Int): Int {
        return i
    }

    // EXPRESSION: myLocalFun5(2)
    // RESULT: 2: I
    //Breakpoint!
    myLocalFun5(2)

    var i = 1
    fun myLocalFun6(): Int {
        i++
        return i
    }

    // EXPRESSION: myLocalFun6()
    // RESULT: 2: I
    //Breakpoint!
    myLocalFun6()

    i = 1
    fun myLocalFun7() {
        // EXPRESSION: myLocalFun6() + 1
        // RESULT: Cannot find local variable 'myLocalFun6' with type kotlin.jvm.functions.Function0
        //Breakpoint!
        myLocalFun6() + 1
    }

    myLocalFun7()

    fun <T> myLocalFun8(): T = 1 as T

    // EXPRESSION: myLocalFun8<Int>()
    // RESULT: 1: I
    //Breakpoint!
    myLocalFun8<Int>()
}

// Muted on IR evaluator: working as intended
// The failing tests _pass_ on the IR backend as the compilations scheme for local functions support this pattern.
// Local functions are lifted as static functions, captures closed over with parameters. There is thus no `FunctionN` instance
// object that falls out of scope at run-time, which is what this test illustrates happening on the JVM backend.