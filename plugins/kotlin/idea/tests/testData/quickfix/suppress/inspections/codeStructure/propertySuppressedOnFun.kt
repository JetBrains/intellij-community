// "Suppress 'RemoveRedundantBackticks' for fun propertySuppressedOnFun" "true"

fun propertySuppressedOnFun() {
    var <caret>`tick` = 0
}

// K1_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.RemoveRedundantBackticksInspection
// K2_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.RemoveRedundantBackticksInspection
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
// FUS_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
