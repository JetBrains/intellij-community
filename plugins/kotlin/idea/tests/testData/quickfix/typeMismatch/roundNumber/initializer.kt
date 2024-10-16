// "Round using roundToLong()" "true"
// WITH_STDLIB
fun test(d: Double) {
    val x: Long = d<caret>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RoundNumberFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.RoundNumberFix