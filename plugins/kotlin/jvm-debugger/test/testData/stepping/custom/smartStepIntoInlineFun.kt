package smartStepIntoInlinedFunLiteral

fun main(args: Array<String>) {
    val array = arrayOf(1, 2)
    val myClass = MyClass()

    //Breakpoint!
    test()
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 1
    myClass.f1 { test() }
           .f2 { test() }

    // RESUME: 1
    //Breakpoint!
    test()
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 3
    myClass.f1 { test() }
        .f2 { test() }

    // RESUME: 1
    //Breakpoint!
    test()
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 1
    array.map {
        it *2
    }

    // RESUME: 1
    //Breakpoint!
    test()
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 1
    array.map { it * 2 }
         .filter {
             it > 2
         }

    // RESUME: 1
    //Breakpoint!
    test()
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 3
    array.map { it * 2 }
        .filter {
            it > 2
        }

    // RESUME: 1
    //Breakpoint!
    test()
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 1
    myClass.f1 { test() }.f2 { test() }

    // RESUME: 1
    //Breakpoint!
    test()
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 3
    myClass.f1 { test() }.f2 { test() }
}

class MyClass {
    inline fun f1(f1Param: () -> Unit): MyClass {
        test()
        f1Param()
        return this
    }

    inline fun f2(f1Param: () -> Unit): MyClass {
        test()
        f1Param()
        return this
    }
}

fun test() {}

// IGNORE_K2