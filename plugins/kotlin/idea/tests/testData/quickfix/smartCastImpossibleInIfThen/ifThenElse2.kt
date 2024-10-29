// "Replace 'if' expression with elvis expression" "true"
class Test {
    var x: String? = ""

    fun test() {
        val i = if (x != null) {
            <caret>x.length
        } else {
            0
        }
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SmartCastImpossibleInIfThenFactory$createQuickFix$1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.IfThenToElviFix$asModCommandAction$1