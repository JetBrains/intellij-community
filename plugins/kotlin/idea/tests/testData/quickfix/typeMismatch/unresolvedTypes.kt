// "Change type of 'transformer' to '(Int) -> R'" "false"
// WITH_STDLIB
// K2_AFTER_ERROR: Argument type mismatch: actual type is 'String', but 'Function1<Int, uninferred R (of fun <T, R> Iterable<T>.map)>' was expected.
// K2_AFTER_ERROR: Cannot infer type for this parameter. Specify it explicitly.
// ERROR: Type mismatch: inferred type is String but (TypeVariable(T)) -> TypeVariable(R) was expected

fun stdlibFunctionTest() {
    val numbers = listOf(1, 2, 3)
    val transformer: String = "not a function"
    numbers.map(<caret>transformer)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix