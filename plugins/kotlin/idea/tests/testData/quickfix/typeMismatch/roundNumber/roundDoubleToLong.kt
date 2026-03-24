// "Round using roundToLong()" "true"
// PRIORITY: LOW
// WITH_STDLIB
// K2_ERROR: Argument type mismatch: actual type is 'Double', but 'Long' was expected.
fun test(d: Double) {
    bar(d<caret>)
}

fun bar(x: Long) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RoundNumberFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.RoundNumberFix