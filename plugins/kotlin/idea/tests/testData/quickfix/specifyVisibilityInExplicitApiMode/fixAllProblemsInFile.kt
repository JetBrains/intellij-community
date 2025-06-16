// "Apply all 'Make public explicitly' fixes in file" "true"
// COMPILER_ARGUMENTS: -Xexplicit-api=strict
// K2_AFTER_ERROR: Return type must be specified in explicit API mode.
// K2_AFTER_ERROR: Return type must be specified in explicit API mode.

class Test {
    fun foo<caret>() = 1
    fun bar() = 2
}
// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInsight.daemon.impl.actions.FixAllHighlightingProblems