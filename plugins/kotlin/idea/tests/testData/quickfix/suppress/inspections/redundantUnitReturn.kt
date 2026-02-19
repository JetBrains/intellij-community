// "Suppress 'RedundantUnitReturnType' for file ${file}" "true"

fun foo(): Unit<caret> {

}

// K1_TOOL: org.jetbrains.kotlin.idea.inspections.RedundantUnitReturnTypeInspection
// K2_TOOL: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased.RedundantUnitReturnTypeInspection
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
// FUS_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix