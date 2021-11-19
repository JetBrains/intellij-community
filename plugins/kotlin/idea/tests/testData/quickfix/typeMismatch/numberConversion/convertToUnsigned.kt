// "Convert expression to 'UInt'" "true"
// WITH_STDLIB
fun foo(param: UInt) {}

fun test(expr: Int) {
    foo(<caret>expr)
}