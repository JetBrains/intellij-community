// "Suppress 'RemoveEmptyClassBody' for interface InterfaceSuppressedOnInterface" "true"

interface InterfaceSuppressedOnInterface {<caret>}

// K1_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.RemoveEmptyClassBodyInspection
// K2_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.RemoveEmptyClassBodyInspection
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
// FUS_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
