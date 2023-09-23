package suspendFunctionWithInlineClass

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2)

inline class A(val str: String)

suspend fun createA() = A("TEXT")

fun main() {
    // EXPRESSION: createA()
    // RESULT: "A(str=TEXT)": Ljava/lang/String;
    //Breakpoint!
    println("")
}
