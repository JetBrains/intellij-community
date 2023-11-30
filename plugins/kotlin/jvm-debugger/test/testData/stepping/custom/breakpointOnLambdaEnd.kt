package breakpointOnLambdaEnd

fun <T> lambda(obj: T, f: T.() -> Unit) = f(obj)
fun <T> inlineLambda(obj: T, f: T.() -> Unit) = f(obj)
fun foo() = Unit
fun main() {
    lambda("first") {
        lambda("second") {
            // STEP_INTO: 1
            // RESUME: 1
            //Breakpoint!
            foo() } }

    // RESUME: 1
    inlineLambda("first") {
        inlineLambda("second") {
            // STEP_INTO: 1
            // RESUME: 1
            //Breakpoint!
            foo() } }
}
