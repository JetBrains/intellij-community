// "Apply all 'Specify type explicitly' fixes in file" "true"
// COMPILER_ARGUMENTS: -Xexplicit-api=warning

class Test {
    fun foo<caret>() = 1
    fun bar() = 2
}
// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInsight.daemon.impl.actions.FixAllHighlightingProblems