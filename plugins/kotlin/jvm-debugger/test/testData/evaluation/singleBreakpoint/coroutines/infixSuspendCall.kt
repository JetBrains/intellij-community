
// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2)

suspend infix fun String.myInfixPlus(s: String): String {
    return this + s
}

fun main() {
    //Breakpoint!
    println()
}

// EXPRESSION: "hello " myInfixPlus "world"
// RESULT: "hello world": Ljava/lang/String;
