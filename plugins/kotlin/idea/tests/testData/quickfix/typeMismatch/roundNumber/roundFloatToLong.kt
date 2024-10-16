// "Round using roundToLong()" "true"
// WITH_STDLIB
fun test(f: Float) {
    bar(f<caret>)
}

fun bar(x: Long) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RoundNumberFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.RoundNumberFix