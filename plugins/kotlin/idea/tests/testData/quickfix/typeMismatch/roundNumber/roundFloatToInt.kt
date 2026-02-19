// "Round using roundToInt()" "true"
// PRIORITY: LOW
// WITH_STDLIB
fun test(f: Float) {
    foo(f<caret>)
}

fun foo(x: Int) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RoundNumberFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.RoundNumberFix