// "Replace 'if' expression with elvis expression" "true"
// WITH_STDLIB
class Test {
    var x: String? = ""

    fun test() {
        val i = if (x != null) foo(<caret>x) else bar()
    }

    fun foo(s: String) = 1

    fun bar() = 0
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SmartCastImpossibleInIfThenFactory$createQuickFix$1