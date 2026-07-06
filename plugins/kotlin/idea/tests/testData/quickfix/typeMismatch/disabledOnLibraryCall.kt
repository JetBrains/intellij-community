// "Change parameter 'transform' type of function 'map' to 'String'" "false"
// ERROR: Type mismatch: inferred type is String but (TypeVariable(T)) -> TypeVariable(R) was expected
// WITH_STDLIB
// K2_AFTER_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_AFTER_ERROR: CANNOT_INFER_PARAMETER_TYPE
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: CANNOT_INFER_PARAMETER_TYPE

fun foo(param: String) {
    listOf(1, 2, 3).map(para<caret>m)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeParameterTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeParameterTypeFix