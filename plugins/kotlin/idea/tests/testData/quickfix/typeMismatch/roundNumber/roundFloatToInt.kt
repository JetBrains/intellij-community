// "Round using roundToInt()" "true"
// WITH_STDLIB
fun test(f: Float) {
    foo(f<caret>)
}

fun foo(x: Int) {}