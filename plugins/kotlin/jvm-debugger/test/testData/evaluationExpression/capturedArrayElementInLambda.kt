// SHOW_KOTLIN_VARIABLES

fun foo(f: () -> Unit) = f()

fun main() {
    val a = IntArray(10)
    val b = arrayOf(1, 2, 3)
    foo {
        //Breakpoint!
        a[0] = 10
        b[0] = 10
    }
}

// IDEA-379138: a variable captured in a lambda body must be evaluated by its source name, not `$a`.
// VARIABLE_PATH: a
// EVAL_EXPRESSION: a

// VARIABLE_PATH: a.0
// EVAL_EXPRESSION: (a as int[])[0]

// VARIABLE_PATH: b
// EVAL_EXPRESSION: b

// VARIABLE_PATH: b.0
// EVAL_EXPRESSION: (b as Integer[])[0]
