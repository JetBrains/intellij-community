// "Suppress 'RedundantModalityModifier' for class CompanionSuppressedOnClass" "true"

class CompanionSuppressedOnClass {
    <caret>final companion object
}

// K1_TOOL: org.jetbrains.kotlin.idea.inspections.RedundantModalityModifierInspection
// K2_TOOL: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased.RedundantModalityModifierInspection
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
// FUS_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
