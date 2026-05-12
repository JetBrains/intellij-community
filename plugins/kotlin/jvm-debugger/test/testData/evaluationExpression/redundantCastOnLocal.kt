class Foo(val bar: Bar)
class Bar(val s: String)

fun main() {
    val foo = Foo(Bar("bar"))
    //Breakpoint!
    println()
}

// VARIABLE_PATH: foo
// EVAL_EXPRESSION: foo

// VARIABLE_PATH: foo.bar
// EVAL_EXPRESSION: foo.bar

// VARIABLE_PATH: foo.bar.s
// EVAL_EXPRESSION: foo.bar.s
