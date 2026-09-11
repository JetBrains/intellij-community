// "Round using roundToInt()" "true"
// PRIORITY: LOW
// WITH_STDLIB
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
fun test(d: Double) {
    foo(d<caret>)
}

fun foo(x: Int) {}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.RoundNumberFix