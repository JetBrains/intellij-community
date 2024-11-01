package smartStepIntoLambdaWithDefaultArgsFun

fun toplevelFun(id: Int = 100, lam: (Int) -> Int) = lam(id)

fun testTopLevelFun() {
    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = -1
    toplevelFun(42, {
        it
    })

    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = -1
    toplevelFun(lam = {
        it
    })
}

class Clazz {
    fun classFun(id: Int = 100, lam: (Int) -> Int) = lam(id)
}

fun testClassFun1() {
    val c = Clazz()
    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = -1
    c.classFun(42, {
        it
    })
}

fun testClassFun2() {
    val c = Clazz()
    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = -1
    c.classFun(lam = {
        it
    })
}

fun main() {
    testTopLevelFun()
    testClassFun1()
    testClassFun2()
}
