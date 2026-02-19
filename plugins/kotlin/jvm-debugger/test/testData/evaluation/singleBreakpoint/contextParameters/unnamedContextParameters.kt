// See KT-80227
// IGNORE_K2

// ATTACH_LIBRARY: contexts
// ENABLED_LANGUAGE_FEATURE: ContextParameters

package unnamedContextParameters

import forTests.*


context(ctx2: Ctx2, ctx1: Ctx1)
fun bar(x: Int) = x + ctx2.foo2() + ctx1.foo1()

context(_: Ctx1, _: Ctx2)
fun check(x: Int) {
    // EXPRESSION: bar(42)
    // RESULT: 45: I
    //Breakpoint!
    bar(x)
}

fun main() {
    context(Ctx1(), Ctx2()) {
        check(10)
    }
}

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES
// IGNORE_OLD_BACKEND
