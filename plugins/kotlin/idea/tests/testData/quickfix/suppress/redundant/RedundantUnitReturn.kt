// "Remove suppression" "true"

@Suppress("<caret>RedundantUnitReturnType")
fun foo() {
}

// K1_TOOL: org.jetbrains.kotlin.idea.inspections.RedundantUnitReturnTypeInspection
// K2_TOOL: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased.RedundantUnitReturnTypeInspection
// TOOL: com.intellij.codeInspection.RedundantSuppressInspection
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.RemoveRedundantSuppression
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.RemoveRedundantSuppression