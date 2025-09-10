package suspendFunctionWithInlineClass

// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.4.2.jar)

inline class A(val str: String)

suspend fun createA() = A("TEXT")

fun main() {
    // EXPRESSION: createA()
    // RESULT: instance of suspendFunctionWithInlineClass.A(id=ID): LsuspendFunctionWithInlineClass/A;
    //Breakpoint!
    println("")
}
