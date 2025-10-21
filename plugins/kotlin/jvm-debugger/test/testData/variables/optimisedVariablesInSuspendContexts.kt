package optimisedVariablesInSuspendContexts

// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.4.2.jar)
// SHOW_KOTLIN_VARIABLES

import kotlinx.coroutines.runBlocking

suspend fun foo() {
    val a = 1
    val b = 1
    //Breakpoint!
    println("")
    suspendUse(a)
    //Breakpoint!
    println("")
    suspendUse(b)
    //Breakpoint!
    println("")
}

fun main() = runBlocking {
    val a = 1
    val b = 1
    //Breakpoint!
    println("")
    suspendUse(a)
    //Breakpoint!
    println("")
    use(b)
    //Breakpoint!
    println("")
    foo()
}

fun use(value: Any) {

}

suspend fun suspendUse(value: Any) {

}

