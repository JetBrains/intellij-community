// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package smartStepIntoInlineLambdaMixedWithOrdinaryOnLine

//Breakpoint!
fun foo() = 0

fun foo1() = 1
fun foo2() = 2
fun foo3() = 3
fun foo4() = 4
fun foo5() = 5

inline fun Int.bar(f: () -> Int) = f()
fun Int.bar(x: Int, f: () -> Int) = f()

data class A(val x: Int, val y: Int)

inline fun A.inlineFoo(f: (Int, A, Int) -> Unit) = f(1, this, 1)
fun A.foo(f: (Int, A, Int) -> Unit) = f(1, this, 1)

fun main() {
    foo().bar { foo1() }.bar(0) { foo2() }.bar { foo3() }.bar(0) { foo4() }.bar { foo5() }

    val a = A(1, 2)
    a.foo { x, y, z ->
        foo().bar { foo1() }.bar(0) { foo2() }.bar { foo3() }.bar(0) { foo4() }.bar { foo5() }
    }

    a.foo { x, (y, z), q ->
        foo().bar { foo1() }.bar(0) { foo2() }.bar { foo3() }.bar(0) { foo4() }.bar { foo5() }
    }

    a.foo { x,
            (y, z),
            q ->
        foo().bar { foo1() }.bar(0) { foo2() }.bar { foo3() }.bar(0) { foo4() }.bar { foo5() }
    }

    a.inlineFoo { x, y, z ->
        foo().bar { foo1() }.bar(0) { foo2() }.bar { foo3() }.bar(0) { foo4() }.bar { foo5() }
    }

    a.inlineFoo { x, (y, z), q ->
        foo().bar { foo1() }.bar(0) { foo2() }.bar { foo3() }.bar(0) { foo4() }.bar { foo5() }
    }

    a.inlineFoo { x,
                 (y, z),
                 q ->
        foo().bar { foo1() }.bar(0) { foo2() }.bar { foo3() }.bar(0) { foo4() }.bar { foo5() }
    }
}

// STEP_OVER: 1
// SMART_STEP_INTO_BY_INDEX: 3
// STEP_INTO: 1
// STEP_OVER: 2
// SMART_STEP_INTO_BY_INDEX: 5
// STEP_INTO: 1
// STEP_OVER: 3
// SMART_STEP_INTO_BY_INDEX: 7
// STEP_INTO: 1
// STEP_OVER: 2
// SMART_STEP_INTO_BY_INDEX: 9
// STEP_INTO: 1
// STEP_OVER: 3
// SMART_STEP_INTO_BY_INDEX: 11
// STEP_INTO: 1
// RESUME: 1


// STEP_OVER: 1
// SMART_STEP_INTO_BY_INDEX: 3
// STEP_INTO: 1
// STEP_OVER: 2
// SMART_STEP_INTO_BY_INDEX: 5
// STEP_INTO: 1
// STEP_OVER: 3
// SMART_STEP_INTO_BY_INDEX: 7
// STEP_INTO: 1
// STEP_OVER: 2
// SMART_STEP_INTO_BY_INDEX: 9
// STEP_INTO: 1
// STEP_OVER: 3
// SMART_STEP_INTO_BY_INDEX: 11
// STEP_INTO: 1
// RESUME: 1

// STEP_OVER: 1
// SMART_STEP_INTO_BY_INDEX: 3
// STEP_INTO: 1
// STEP_OVER: 2
// SMART_STEP_INTO_BY_INDEX: 5
// STEP_INTO: 1
// STEP_OVER: 3
// SMART_STEP_INTO_BY_INDEX: 7
// STEP_INTO: 1
// STEP_OVER: 2
// SMART_STEP_INTO_BY_INDEX: 9
// STEP_INTO: 1
// STEP_OVER: 3
// SMART_STEP_INTO_BY_INDEX: 11
// STEP_INTO: 1
// RESUME: 1

// STEP_OVER: 1
// SMART_STEP_INTO_BY_INDEX: 3
// STEP_INTO: 1
// STEP_OVER: 2
// SMART_STEP_INTO_BY_INDEX: 5
// STEP_INTO: 1
// STEP_OVER: 3
// SMART_STEP_INTO_BY_INDEX: 7
// STEP_INTO: 1
// STEP_OVER: 2
// SMART_STEP_INTO_BY_INDEX: 9
// STEP_INTO: 1
// STEP_OVER: 3
// SMART_STEP_INTO_BY_INDEX: 11
// STEP_INTO: 1
// RESUME: 1

// STEP_OVER: 1
// SMART_STEP_INTO_BY_INDEX: 3
// STEP_INTO: 1
// STEP_OVER: 2
// SMART_STEP_INTO_BY_INDEX: 5
// STEP_INTO: 1
// STEP_OVER: 3
// SMART_STEP_INTO_BY_INDEX: 7
// STEP_INTO: 1
// STEP_OVER: 2
// SMART_STEP_INTO_BY_INDEX: 9
// STEP_INTO: 1
// STEP_OVER: 3
// SMART_STEP_INTO_BY_INDEX: 11
// STEP_INTO: 1
// RESUME: 1


// STEP_OVER: 1
// SMART_STEP_INTO_BY_INDEX: 3
// STEP_INTO: 1
// STEP_OVER: 2
// SMART_STEP_INTO_BY_INDEX: 5
// STEP_INTO: 1
// STEP_OVER: 3
// SMART_STEP_INTO_BY_INDEX: 7
// STEP_INTO: 1
// STEP_OVER: 2
// SMART_STEP_INTO_BY_INDEX: 9
// STEP_INTO: 1
// STEP_OVER: 3
// SMART_STEP_INTO_BY_INDEX: 11
// STEP_INTO: 1
// RESUME: 1

// STEP_OVER: 1
// SMART_STEP_INTO_BY_INDEX: 3
// STEP_INTO: 1
// STEP_OVER: 2
// SMART_STEP_INTO_BY_INDEX: 5
// STEP_INTO: 1
// STEP_OVER: 3
// SMART_STEP_INTO_BY_INDEX: 7
// STEP_INTO: 1
// STEP_OVER: 2
// SMART_STEP_INTO_BY_INDEX: 9
// STEP_INTO: 1
// STEP_OVER: 3
// SMART_STEP_INTO_BY_INDEX: 11
// STEP_INTO: 1
// RESUME: 1

// IGNORE_K2
