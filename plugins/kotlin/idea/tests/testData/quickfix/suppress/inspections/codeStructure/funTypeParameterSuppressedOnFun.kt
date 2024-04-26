// "Suppress 'RemoveRedundantBackticks' for fun funTypeParameterSuppressedOnFun" "true"

fun <<caret>`T`> funTypeParameterSuppressedOnFun() {}

// K1_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.RemoveRedundantBackticksInspection
// K2_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.RemoveRedundantBackticksInspection
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
// FUS_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
