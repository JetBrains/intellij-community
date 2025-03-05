// MODULE: jvm-lib
// FILE: decl.kt

class Clazz {
    internal inline fun square(arg: Int = 42) = arg*arg
}

// MODULE: jvm-app(jvm-lib)
// FILE: call.kt

public fun main() {
    // EXPRESSION: Clazz().square()
    // RESULT: 1764: I
    //Breakpoint!
    val x = 1
}
