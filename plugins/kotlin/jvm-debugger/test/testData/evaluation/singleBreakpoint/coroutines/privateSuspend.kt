package privateSuspend

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2)

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

@JvmInline
value class IC(val x: Int)

object Obj {
    private suspend fun privateStringMemberFun(): String {
        delay(1)
        return "a"
    }

    private suspend fun privateIntMemberFun(): Int {
        delay(1)
        return 0
    }

    private suspend fun privateICMemberFun(): IC {
        delay(1)
        return IC(0)
    }
}

private suspend fun privateStringTopLevelFun(): String {
    delay(1)
    return "b"
}

private suspend fun privateIntTopLevelFun(): Int {
    delay(1)
    return 1
}

private suspend fun privateICClassTopLevelFun(): IC {
    delay(1)
    return IC(1)
}

fun main() = runBlocking {
    //Breakpoint!
    val x = 1
}

// EXPRESSION: Obj.privateStringMemberFun()
// RESULT: "a": Ljava/lang/String;

// EXPRESSION: Obj.privateIntMemberFun()
// RESULT: 0: I

// EXPRESSION: Obj.privateICMemberFun().x
// RESULT: 0: I

// EXPRESSION: privateStringTopLevelFun()
// RESULT: "b": Ljava/lang/String;

// EXPRESSION: privateIntTopLevelFun()
// RESULT: 1: I

// EXPRESSION: privateICClassTopLevelFun().x
// RESULT: 1: I