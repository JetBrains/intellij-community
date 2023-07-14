// "Round using roundToInt()" "true"
// WITH_STDLIB
fun test(d: Double) {
    foo(d<caret>)
}

fun foo(x: Int) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RoundNumberFix