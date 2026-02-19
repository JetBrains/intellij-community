// "Suppress 'RedundantVisibilityModifier' for companion object Companion of CompanionSuppressedOnCompanion" "true"

class CompanionSuppressedOnCompanion {
    <caret>public companion object
}

// K1_TOOL: org.jetbrains.kotlin.idea.inspections.RedundantVisibilityModifierInspection
// K2_TOOL: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased.RedundantVisibilityModifierInspection
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
// FUS_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
