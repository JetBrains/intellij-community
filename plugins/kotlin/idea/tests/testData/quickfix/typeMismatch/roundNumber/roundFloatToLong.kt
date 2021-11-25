// "Round using roundToLong()" "true"
// WITH_STDLIB
fun test(f: Float) {
    bar(f<caret>)
}

fun bar(x: Long) {}