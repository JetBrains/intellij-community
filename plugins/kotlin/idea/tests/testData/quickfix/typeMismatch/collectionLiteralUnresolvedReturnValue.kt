// "Change return type of enclosing function 'foo' to 'List<String>'" "true"
// K2_ERROR: UNRESOLVED_COLLECTION_LITERAL
// COMPILER_ARGUMENTS: -Xcollection-literals

fun foo(a: String): String {
    return [""<caret>]
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix