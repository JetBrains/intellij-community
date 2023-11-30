// "Suppress 'RemoveRedundantBackticks' for class EnumEntrySuppressedOnClass" "true"

enum class EnumEntrySuppressedOnClass {
    <caret>`VALUE_A`, VALUE_B
}

// K1_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.RemoveRedundantBackticksInspection
// K2_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.RemoveRedundantBackticksInspection
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
// FUS_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
