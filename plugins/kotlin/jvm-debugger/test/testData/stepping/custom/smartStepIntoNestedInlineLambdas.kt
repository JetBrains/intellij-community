package smartStepIntoNestedInlineLambdas

fun Int.bar(x: Int, f: () -> Int) = f()
inline fun Int.bar(f: () -> Int) = f()
inline fun Int.baz(f: () -> Int) =
    //Breakpoint!
    f()

fun foo1() = 1
fun foo2() = 2
fun foo3() = 3

fun inlineFun() {
    4.bar { 5.baz {
        1.bar { foo1() }.bar(0) { foo2() }.bar { foo3() }
    } }
}

fun nestedInlineFun1() {
    inlineFun()
}

fun nestedInlineFun2() {
    nestedInlineFun1()
}

fun main() {
    inlineFun()
    nestedInlineFun1()
    nestedInlineFun2()
}

// STEP_INTO: 1
// SMART_STEP_INTO_BY_INDEX: 2
// STEP_INTO: 1
// STEP_OVER: 2
// SMART_STEP_INTO_BY_INDEX: 4
// STEP_INTO: 1
// STEP_OVER: 3
// SMART_STEP_INTO_BY_INDEX: 6
// STEP_INTO: 1
// RESUME: 1

// STEP_INTO: 1
// SMART_STEP_INTO_BY_INDEX: 2
// STEP_INTO: 1
// STEP_OVER: 2
// SMART_STEP_INTO_BY_INDEX: 4
// STEP_INTO: 1
// STEP_OVER: 3
// SMART_STEP_INTO_BY_INDEX: 6
// STEP_INTO: 1
// RESUME: 1

// STEP_INTO: 1
// SMART_STEP_INTO_BY_INDEX: 2
// STEP_INTO: 1
// STEP_OVER: 2
// SMART_STEP_INTO_BY_INDEX: 4
// STEP_INTO: 1
// STEP_OVER: 3
// SMART_STEP_INTO_BY_INDEX: 6
// STEP_INTO: 1
// RESUME: 1
