// "Cast expression 'a.foo().single()' to 'Int'" "true"
// WITH_STDLIB
class A {
    fun foo(): List<Any> = listOf()

    fun bar(i : Int, s: String) = Unit

    fun use() {
        val a = A()
        a.bar(a.foo().single<caret>(), "Asd")
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CastExpressionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinApplicatorBasedQuickFix