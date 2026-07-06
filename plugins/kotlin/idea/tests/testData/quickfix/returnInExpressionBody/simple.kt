// "Specify 'String' return type for enclosing function 'a'" "true"
// K2_ERROR: IMPLICIT_NOTHING_RETURN_TYPE
// K2_ERROR: RETURN_IN_FUNCTION_WITH_EXPRESSION_BODY_AND_IMPLICIT_TYPE
// K2_ERROR: RETURN_TYPE_MISMATCH

fun a() = r<caret>eturn ""


// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix