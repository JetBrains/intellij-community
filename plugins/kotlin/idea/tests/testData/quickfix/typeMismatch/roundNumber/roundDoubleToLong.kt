// "Round using roundToLong()" "true"
// WITH_STDLIB
fun test(d: Double) {
    bar(d<caret>)
}

fun bar(x: Long) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RoundNumberFix