// MODULE: jvm-lib
// FILE: decl.kt

inline fun foo(): Int {
    val obj = object {
        fun foo() = 1
    }
    val lambda = { 2 }

    return obj.foo() + lambda()
}

// MODULE: jvm-app(jvm-lib)
// FILE: call.kt

public fun main() {
    // EXPRESSION: foo()
    // RESULT: 3: I
    //Breakpoint!
    val x = 1
}
