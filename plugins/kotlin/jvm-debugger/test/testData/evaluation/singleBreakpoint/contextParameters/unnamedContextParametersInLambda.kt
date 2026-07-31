// ENABLED_LANGUAGE_FEATURE: ContextParameters

package unnamedContextParametersInLambda

context(s: String)
fun foo(x: Int): String = s + x

fun bar(f: context(String) (Int) -> Unit) {
    f("", 10)
}

fun main() {
    bar { x ->
        // EXPRESSION: foo(x)
        // RESULT: "10": Ljava/lang/String;
        //Breakpoint!
        foo(x)
    }
}
