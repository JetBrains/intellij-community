// "Suppress 'ConstantConditionIf' for fun foo" "true"

fun foo() {
    if (<caret>true) {
    }
}

// K1_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.ConstantConditionIfInspection
// K2_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.ConstantConditionIfInspection
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
// FUS_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix