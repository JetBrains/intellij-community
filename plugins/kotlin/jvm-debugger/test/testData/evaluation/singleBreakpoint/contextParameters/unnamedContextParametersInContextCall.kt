// ENABLED_LANGUAGE_FEATURE: ContextParameters

package unnamedContextParametersInContextCall

context(s: String)
fun foo(x: Int): String = s + x

fun main() {
    context("") {
        // EXPRESSION: foo(5)
        // RESULT: "5": Ljava/lang/String;
        //Breakpoint!
        foo(5)
    }
}
