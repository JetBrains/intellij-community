// SHOW_KOTLIN_VARIABLES

fun foo(f: () -> Unit) = f()

fun main() {
    val s = intArrayOf(31, 32)
    val n = 1
    foo {
        println(s[0])
        println(n)
        run {
            val s = "shadow"
            val n = 2
            //Breakpoint!
            println(s + n)
        }
    }
}

// KTIJ-41215: a local shadows a captured variable, so the frame shows only the local, and its prefill evaluates to it.
// VARIABLE_PATH: s
// EVAL_EXPRESSION: s
// EVAL_RESULT: shadow

// VARIABLE_PATH: n
// EVAL_EXPRESSION: n
// EVAL_RESULT: 2
