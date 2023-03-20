// ATTACH_LIBRARY: contexts
// ENABLED_LANGUAGE_FEATURE: ContextReceivers

package contextReceiversInSamLambdas

import forTests.*

fun runLambda(runnable: Runnable) = runnable.run()

context(Ctx1, Ctx2)
fun test() {
    runLambda {
        // EXPRESSION: name1
        // RESULT: "ctx1": Ljava/lang/String;
        // EXPRESSION: name2
        // RESULT: "ctx2": Ljava/lang/String;
        // EXPRESSION: this@Ctx1.name
        // RESULT: "ctx1": Ljava/lang/String;
        // EXPRESSION: this@Ctx2.name
        // RESULT: "ctx2": Ljava/lang/String;
        // EXPRESSION: this@Ctx1
        // RESULT: instance of forTests.Ctx1(id=ID): LforTests/Ctx1;
        // EXPRESSION: this@Ctx2
        // RESULT: instance of forTests.Ctx2(id=ID): LforTests/Ctx2;
        // EXPRESSION: foo1()
        // RESULT: 1: I
        // EXPRESSION: foo2()
        // RESULT: 2: I
        // EXPRESSION: this@Ctx1.bar()
        // RESULT: 1: I
        // EXPRESSION: this@Ctx2.bar()
        // RESULT: 2: I
        //Breakpoint!
        println(name1)
        println(name2)
    }
}

fun main() {
    with (Ctx1()) {
        with (Ctx2()) {
            test()
        }
    }
}

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES
// IGNORE_OLD_BACKEND