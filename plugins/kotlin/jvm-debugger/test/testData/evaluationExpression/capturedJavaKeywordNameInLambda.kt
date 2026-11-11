// SHOW_KOTLIN_VARIABLES

fun foo(f: () -> Unit) = f()

fun main() {
    val default = "hello"
    foo {
        //Breakpoint!
        println(default)
    }
}

// A source name that is a Java keyword is not supported: the prefill keeps the synthesized name and does not fail.
// VARIABLE_PATH: default
// EVAL_EXPRESSION: $default
