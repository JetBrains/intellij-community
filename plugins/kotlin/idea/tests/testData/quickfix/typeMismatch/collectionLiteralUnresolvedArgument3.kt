// "Change type of 'a' to 'List<T>'" "false"
// K2_ERROR: UNRESOLVED_COLLECTION_LITERAL
// COMPILER_ARGUMENTS: -Xcollection-literals
// K2_AFTER_ERROR: UNRESOLVED_COLLECTION_LITERAL

fun target(a: Int) {}

fun <T> reproduce(value: T) {
    target([value]<caret>) // [UNRESOLVED_COLLECTION_LITERAL]
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix