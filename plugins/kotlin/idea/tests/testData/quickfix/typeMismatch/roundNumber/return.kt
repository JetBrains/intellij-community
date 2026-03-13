// "Round using roundToInt()" "true"
// PRIORITY: LOW
// WITH_STDLIB
// K2_ERROR: Return type mismatch: expected 'Int', actual 'Double'.
fun test(d: Double): Int {
    return d<caret>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RoundNumberFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.RoundNumberFix