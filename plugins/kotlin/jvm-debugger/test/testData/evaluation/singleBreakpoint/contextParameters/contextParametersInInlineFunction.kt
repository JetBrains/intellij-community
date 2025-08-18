// ATTACH_LIBRARY: contexts
// ENABLED_LANGUAGE_FEATURE: ContextParameters

package contextParametersInInlineFunction

import forTests.*

context(ctx1: Ctx1)
inline fun ctx1Foo() = ctx1.foo1()

context(ctx2: Ctx2)
inline fun ctx2Foo() = ctx2.foo2()

context(ctx2: Ctx2)
inline fun bar() = ctx2.bar()

context(ctx1: Ctx1, ctx2: Ctx2)
inline fun foo() {
    // EXPRESSION: ctx1.name1
    // RESULT: "ctx1": Ljava/lang/String;
    // EXPRESSION: ctx2.name2
    // RESULT: "ctx2": Ljava/lang/String;
    // EXPRESSION: ctx1
    // RESULT: instance of forTests.Ctx1(id=ID): LforTests/Ctx1;
    // EXPRESSION: ctx2
    // RESULT: instance of forTests.Ctx2(id=ID): LforTests/Ctx2;
    // EXPRESSION: ctx1.foo1()
    // RESULT: 1: I
    // EXPRESSION: ctx2.foo2()
    // RESULT: 2: I
    // EXPRESSION: ctx1.bar()
    // RESULT: 1: I
    // EXPRESSION: ctx2.bar()
    // RESULT: 2: I
    // EXPRESSION: ctx1Foo()
    // RESULT: 1: I
    // EXPRESSION: ctx2Foo()
    // RESULT: 2: I
    // EXPRESSION: bar()
    // RESULT: 2: I
    //Breakpoint!
    println()
}

fun main() {
    with (Ctx1()) {
        with (Ctx2()) {
            foo()
        }
    }
}

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES
// IGNORE_OLD_BACKEND
