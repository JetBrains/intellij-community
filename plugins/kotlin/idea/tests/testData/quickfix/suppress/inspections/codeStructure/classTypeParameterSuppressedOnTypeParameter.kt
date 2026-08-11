// "Suppress 'RemoveRedundantBackticks' for type parameter T" "true"

private class ClassTypeParameterSuppressedOnTypeParameter<<caret>`T`>

// K1_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.RemoveRedundantBackticksInspection
// K2_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.RemoveRedundantBackticksInspection
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
// FUS_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
