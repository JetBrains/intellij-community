// ATTACH_LIBRARY: contexts
// ENABLED_LANGUAGE_FEATURE: ContextReceivers

package functionCallWithImplicitContextClassReceiverCapturing

import forTests.*

context(Ctx1)
fun useWithCtx1() = 1

context(Ctx2)
fun useWithCtx2() = 2

context(Ctx1, Ctx2)
fun useWithCtx1Ctx2() = 3

context(Ctx1, Ctx2, Ctx3)
fun useWithCtx1Ctx2Ctx3() = 4

context(Ctx1, Ctx2, Ctx3, Ctx4)
fun useWithCtx1Ctx2Ctx3Ctx4() = 5

context(Ctx1, Ctx2)
class Test {
    context(Ctx3, Ctx4)
    fun foo() {
        // EXPRESSION: useWithCtx1()
        // RESULT: 1: I
        // EXPRESSION: useWithCtx2()
        // RESULT: 2: I
        // EXPRESSION: useWithCtx1Ctx2()
        // RESULT: 3: I
        // EXPRESSION: useWithCtx1Ctx2Ctx3()
        // RESULT: 4: I
        // EXPRESSION: useWithCtx1Ctx2Ctx3Ctx4()
        // RESULT: 5: I
        //Breakpoint!
        println()
    }
}

fun main() {
    with(Ctx1()) {
        with(Ctx2()) {
            with(Ctx3()) {
                with(Ctx4()) {
                    Test().foo()
                }
            }
        }
    }
}

// IGNORE_OLD_BACKEND
