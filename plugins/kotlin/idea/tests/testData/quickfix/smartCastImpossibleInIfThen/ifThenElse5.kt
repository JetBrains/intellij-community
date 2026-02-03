// "Replace 'if' expression with elvis expression" "true"
// WITH_STDLIB
class Test {
    var x: Any? = null

    fun test() {
        val i = if (x is String) foo(<caret>x) else bar()
    }

    fun foo(s: String) = 1

    fun bar() = 0
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SmartCastImpossibleInIfThenFactory$createQuickFix$1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.IfThenToElviFix$asModCommandAction$1