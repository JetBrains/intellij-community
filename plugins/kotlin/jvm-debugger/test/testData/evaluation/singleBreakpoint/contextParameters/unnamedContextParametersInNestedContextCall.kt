// ENABLED_LANGUAGE_FEATURE: ContextParameters

package unnamedContextParametersInNestedContextCall

context(s: String, x: Int)
fun foo(): String = s + x

fun main() {
    context(42){
        context("outer-") {
            context("inner-") {
                // EXPRESSION: foo()
                // RESULT: "inner-42": Ljava/lang/String;
                //Breakpoint!
                foo()
            }
        }
    }
}
