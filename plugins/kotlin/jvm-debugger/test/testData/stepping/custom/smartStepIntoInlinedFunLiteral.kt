package smartStepIntoInlinedFunLiteral

fun main(args: Array<String>) {
    val array = arrayOf(1, 2)
    val myClass = MyClass()

    //Breakpoint!
    test()
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 4
    // smart step into f2.invoke(), one-line lambda
    myClass.f1 { test() }
           .f2 { test() }

    // RESUME: 1
    //Breakpoint!
    test()
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // smart step into f1.invoke(), one-line lambda
    myClass.f1 { test() }
        .f2 { test() }

    // RESUME: 1
    //Breakpoint!
    test()
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // smart step into map.invoke(), multiline lambda
    array.map {
        it *2
    }

    // RESUME: 1
    //Breakpoint!
    test()
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 4
    // smart step into filter.invoke()
    array.map { it * 2 }
         .filter {
             it > 2
         }

    // RESUME: 1
    //Breakpoint!
    test()
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // smart step into map.invoke()
    array.map { it * 2 }
        .filter {
            it > 2
        }

    // RESUME: 1
    //Breakpoint!
    test()
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 4
    // smart step into f2.invoke(), one-line lambda
    myClass.f1 { test() }.f2 { test() }

    // RESUME: 1
    //Breakpoint!
    test()
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // smart step into f1.invoke(), one-line lambda
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