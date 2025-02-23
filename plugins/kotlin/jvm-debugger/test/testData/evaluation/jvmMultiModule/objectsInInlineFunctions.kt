// MODULE: jvm-lib
// FILE: decl.kt

private inline fun foo(): Int {
    val obj1 = object {
        fun bar(): Int {
            val x = object {
                fun baz() = 2
            }
            return x.baz() + 1
        }
    }

    return obj1.bar()
}

// MODULE: jvm-app()(jvm-lib)
// FILE: call.kt

fun main() {
    // EXPRESSION: foo()
    // RESULT: 3: I
    //Breakpoint!
    val x = 1
}


