// "Change parameter 'transform' type of function 'map' to 'String'" "false"
// K2_AFTER_ERROR: Argument type mismatch: actual type is 'String', but 'Function1<Int, uninferred R (of fun <T, R> Iterable<T>.map)>' was expected.
// K2_AFTER_ERROR: Cannot infer type for this parameter. Specify it explicitly.
// ERROR: Type mismatch: inferred type is String but (TypeVariable(T)) -> TypeVariable(R) was expected
// WITH_STDLIB

fun foo(param: String) {
    listOf(1, 2, 3).map(para<caret>m)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeParameterTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeParameterTypeFix