// "Round using roundToLong()" "true"
// PRIORITY: LOW
// WITH_STDLIB
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
fun test(f: Float) {
    bar(f<caret>)
}

fun bar(x: Long) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RoundNumberFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.RoundNumberFix