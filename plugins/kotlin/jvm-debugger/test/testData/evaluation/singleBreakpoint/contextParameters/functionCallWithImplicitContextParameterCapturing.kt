// ATTACH_LIBRARY: contexts
// ENABLED_LANGUAGE_FEATURE: ContextParameters

package functionCallWithImplicitContextParameterCapturing

import forTests.*

context(ctx1: Ctx1)
fun useWithCtx1() = 1

context(ctx2: Ctx2)
fun useWithCtx2() = 2

context(ctx1: Ctx1, ctx2: Ctx2)
fun useWithCtx1Ctx2() = 3

context(ctx1: Ctx1, ctx2: Ctx2)
fun foo() {
    // EXPRESSION: useWithCtx1()
    // RESULT: 1: I
    // EXPRESSION: useWithCtx2()
    // RESULT: 2: I
    // EXPRESSION: useWithCtx1Ctx2()
    // RESULT: 3: I
    //Breakpoint!
    println()
}

fun main() {
    with(Ctx1()) {
        with(Ctx2()) {
            foo()
        }
    }
}

// IGNORE_OLD_BACKEND
