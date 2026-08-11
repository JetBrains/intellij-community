// "Remove 'val' from parameter" "true"
// K2_ERROR: VAL_OR_VAR_ON_SECONDARY_CONSTRUCTOR_PARAMETER
class A {
    constructor(<caret>val x: Int)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveValVarFromParameterFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveValVarFromParameterFix