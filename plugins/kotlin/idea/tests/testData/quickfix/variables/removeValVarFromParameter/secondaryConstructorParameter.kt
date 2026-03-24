// "Remove 'val' from parameter" "true"
// K2_ERROR: 'val' on secondary constructor parameter is prohibited.
class A {
    constructor(<caret>val x: Int)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveValVarFromParameterFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveValVarFromParameterFix