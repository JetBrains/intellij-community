// "Suppress 'DelegationToVarProperty' for parameter text" "true"

class ParameterSuppressedOnParameter(var <caret>text: CharSequence): CharSequence by text

// K1_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.DelegationToVarPropertyInspection
// K2_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.DelegationToVarPropertyInspection
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
// FUS_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
