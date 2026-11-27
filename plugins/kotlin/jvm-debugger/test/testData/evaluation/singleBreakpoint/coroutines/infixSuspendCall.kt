
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.2.jar)

suspend infix fun String.myInfixPlus(s: String): String {
    return this + s
}

fun main() {
    //Breakpoint!
    println()
}

// EXPRESSION: "hello " myInfixPlus "world"
// RESULT: "hello world": Ljava/lang/String;
