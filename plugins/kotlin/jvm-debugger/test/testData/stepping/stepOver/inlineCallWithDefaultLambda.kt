package inlineCallWithDefaultLambda

fun innerPlain(f: () -> Int = { 1 }) = f()
inline fun innerInline(f: () -> Int = { 2 }) = f()
fun outerCall(p: Int) {}

fun main() {
    // STEP_OVER: 3
    //Breakpoint!
    outerCall(innerPlain())
    outerCall(innerInline())
    outerCall(innerInline { 3 })
}
