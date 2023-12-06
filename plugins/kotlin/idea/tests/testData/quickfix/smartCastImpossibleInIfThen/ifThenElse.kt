// "Replace 'if' expression with elvis expression" "true"
class Test {
    var x: String? = ""

    fun test() {
        val i = if (x != null) <caret>x.length else 0
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SmartCastImpossibleInIfThenFactory$createQuickFix$1