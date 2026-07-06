// "Apply all 'Make public explicitly' fixes in file" "true"
// COMPILER_ARGUMENTS: -Xexplicit-api=strict
// K2_AFTER_ERROR: NO_EXPLICIT_RETURN_TYPE_IN_API_MODE
// K2_AFTER_ERROR: NO_EXPLICIT_RETURN_TYPE_IN_API_MODE
// K2_ERROR: NO_EXPLICIT_RETURN_TYPE_IN_API_MODE
// K2_ERROR: NO_EXPLICIT_RETURN_TYPE_IN_API_MODE
// K2_ERROR: NO_EXPLICIT_VISIBILITY_IN_API_MODE
// K2_ERROR: NO_EXPLICIT_VISIBILITY_IN_API_MODE
// K2_ERROR: NO_EXPLICIT_VISIBILITY_IN_API_MODE

class Test {
    fun foo<caret>() = 1
    fun bar() = 2
}

// FUS_K2_QUICKFIX_NAME: com.intellij.codeInsight.daemon.impl.actions.FixAllHighlightingProblems