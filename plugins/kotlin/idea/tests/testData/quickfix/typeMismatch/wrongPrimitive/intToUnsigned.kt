// "Change to '1u'" "true"
// WITH_STDLIB
fun foo(param: UInt) {}

fun test() {
    foo(<caret>1)
}