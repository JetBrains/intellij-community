// "Change type of 'x' to 'String?'" "true"
// K2_ACTION: "Specify 'String?' type for 'x'" "true"
// K2_ERROR: ASSIGNMENT_TYPE_MISMATCH
fun foo(condition: Boolean) {
    var x = null
    if (condition) {
        x = "abc"<caret>
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix