// "Change type of 'transformer' to '(Int) -> R'" "false"
// WITH_STDLIB
// ERROR: Type mismatch: inferred type is String but (TypeVariable(T)) -> TypeVariable(R) was expected
// K2_AFTER_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_AFTER_ERROR: CANNOT_INFER_PARAMETER_TYPE
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: CANNOT_INFER_PARAMETER_TYPE

fun stdlibFunctionTest() {
    val numbers = listOf(1, 2, 3)
    val transformer: String = "not a function"
    numbers.map(<caret>transformer)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix