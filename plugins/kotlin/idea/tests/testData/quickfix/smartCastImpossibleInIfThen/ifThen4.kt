// "Replace 'if' expression with safe access expression" "true"
// K2_ERROR: Smart cast to 'String' is impossible, because 'x' is a mutable property that could be mutated concurrently.
class Test {
    var x: Any? = null

    fun test() {
        if (x is String) <caret>x.length
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SmartCastImpossibleInIfThenFactory$createQuickFix$1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.IfThenToSafeAccessFix$asModCommandAction$1