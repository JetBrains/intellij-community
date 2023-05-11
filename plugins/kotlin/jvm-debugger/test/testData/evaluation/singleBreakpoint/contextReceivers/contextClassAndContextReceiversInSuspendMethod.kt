// ATTACH_LIBRARY: contexts
// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2)
// ENABLED_LANGUAGE_FEATURE: ContextReceivers

package contextClassAndContextReceiversInSuspendMethod

import kotlinx.coroutines.runBlocking
import forTests.*

context(Ctx1, Ctx2)
class Test {
    context(Ctx3, Ctx4)
    suspend fun foo() {
        // EXPRESSION: name1
        // RESULT: "ctx1": Ljava/lang/String;
        // EXPRESSION: name2
        // RESULT: "ctx2": Ljava/lang/String;
        // EXPRESSION: name3
        // RESULT: "ctx3": Ljava/lang/String;
        // EXPRESSION: name4
        // RESULT: "ctx4": Ljava/lang/String;
        // EXPRESSION: this@Ctx1.name
        // RESULT: "ctx1": Ljava/lang/String;
        // EXPRESSION: this@Ctx2.name
        // RESULT: "ctx2": Ljava/lang/String;
        // EXPRESSION: this@Ctx3.name
        // RESULT: "ctx3": Ljava/lang/String;
        // EXPRESSION: this@Ctx4.name
        // RESULT: "ctx4": Ljava/lang/String;
        // EXPRESSION: this@Ctx1
        // RESULT: instance of forTests.Ctx1(id=ID): LforTests/Ctx1;
        // EXPRESSION: this@Ctx2
        // RESULT: instance of forTests.Ctx2(id=ID): LforTests/Ctx2;
        // EXPRESSION: this@Ctx3
        // RESULT: instance of forTests.Ctx3(id=ID): LforTests/Ctx3;
        // EXPRESSION: this@Ctx4
        // RESULT: instance of forTests.Ctx4(id=ID): LforTests/Ctx4;
        // EXPRESSION: foo1()
        // RESULT: 1: I
        // EXPRESSION: foo2()
        // RESULT: 2: I
        // EXPRESSION: foo3()
        // RESULT: 3: I
        // EXPRESSION: foo4()
        // RESULT: 4: I
        // EXPRESSION: this@Ctx1.bar()
        // RESULT: 1: I
        // EXPRESSION: this@Ctx2.bar()
        // RESULT: 2: I
        // EXPRESSION: this@Ctx3.bar()
        // RESULT: 3: I
        // EXPRESSION: this@Ctx4.bar()
        // RESULT: 4: I
        //Breakpoint!
        println()
    }
}

fun main() = runBlocking {
    with (Ctx1()) {
        with (Ctx2()) {
            with (Ctx3()) {
                with (Ctx4()) {
                    Test().foo()
                }
            }
        }
    }
}

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES
// IGNORE_OLD_BACKEND