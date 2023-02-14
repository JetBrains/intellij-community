// "Round using roundToLong()" "true"
// WITH_STDLIB
fun test(d: Double) {
    bar(d<caret>)
}

fun bar(x: Long) {}