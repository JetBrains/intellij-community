// "Suppress 'RemoveSingleExpressionStringTemplate' for fun <anonymous>" "true"

val statementSuppressedOnAnonymous = fun(ps: String): String { return "<caret>$ps" }

// K1_TOOL: org.jetbrains.kotlin.idea.intentions.RemoveSingleExpressionStringTemplateInspection
// K2_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.RemoveSingleExpressionStringTemplateInspection
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
// FUS_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
