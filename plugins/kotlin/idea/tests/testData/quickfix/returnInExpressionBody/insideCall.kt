// "Specify 'String' return type for enclosing function 'm'" "true"
// K2_ERROR: RETURN_IN_FUNCTION_WITH_EXPRESSION_BODY_AND_IMPLICIT_TYPE
fun foo(s: String): String = s

fun m(a: String?) = foo(a ?: ret<caret>urn "" )


// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix