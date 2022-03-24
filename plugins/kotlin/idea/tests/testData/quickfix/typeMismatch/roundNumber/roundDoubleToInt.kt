// "Round using roundToInt()" "true"
// WITH_STDLIB
fun test(d: Double) {
    foo(d<caret>)
}

fun foo(x: Int) {}