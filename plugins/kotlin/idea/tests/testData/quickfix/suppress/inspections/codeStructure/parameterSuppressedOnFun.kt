// "Suppress 'DoubleNegation' for fun parameterSuppressedOnFun" "true"

fun parameterSuppressedOnFun(suppressMe: Boolean = !!<caret>true) {}

// K1_TOOL: org.jetbrains.kotlin.idea.inspections.KotlinDoubleNegationInspection
// K2_TOOL: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.expressions.KotlinDoubleNegationInspection
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
// FUS_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
