// ENABLED_LANGUAGE_FEATURE: ContextParameters

package unnamedContextParametersInNestedLambda

fun main() {
    context("1") {
        bar { x ->
            // EXPRESSION: foo(x)
            // RESULT: "112": Ljava/lang/String;
            //Breakpoint!
            foo(x)
        }
    }
}

context(s: String, g: String)
fun foo(x: Int): String = s + g + x

fun bar(f: context(Int, String) (Int) -> String): String =
    f(0, "1", 2)
