// "Replace 'if' expression with safe access expression" "true"
// WITH_STDLIB
class Test {
    var x: Any? = null

    fun test() {
        if (x is String) foo(x<caret>)
    }

    fun foo(s: String) = 1
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SmartCastImpossibleInIfThenFactory$createQuickFix$1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.IfThenToSafeAccessFix$asModCommandAction$1