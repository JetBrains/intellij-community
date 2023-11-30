// "Suppress 'WrapUnaryOperator' for class StatementSuppressedOnClass" "true"

class StatementSuppressedOnClass {
    fun context() {
        <caret>-1.inc() + 1
    }
}

// K1_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.WrapUnaryOperatorInspection
// K2_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.WrapUnaryOperatorInspection
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
// FUS_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
