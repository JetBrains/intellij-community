// "Suppress 'DelegationToVarProperty' for file ${file}" "true"

class ParameterSuppressedOnFile(var <caret>text: CharSequence): CharSequence by text

// K1_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.DelegationToVarPropertyInspection
// K2_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.DelegationToVarPropertyInspection
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
// FUS_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
