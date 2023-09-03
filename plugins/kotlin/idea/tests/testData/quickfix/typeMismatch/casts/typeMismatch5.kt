// "Cast expression 'listOf(1)' to 'List<T>'" "true"
// WITH_STDLIB
// ERROR: Type mismatch: inferred type is IntegerLiteralType[Int,Long,Byte,Short] but T was expected
// IGNORE_K1
fun <T> f() {
    val someList: List<T> = lis<caret>tOf(1)
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinApplicatorBasedQuickFix