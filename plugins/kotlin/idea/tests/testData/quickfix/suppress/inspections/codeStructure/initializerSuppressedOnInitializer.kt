// "Suppress 'WrapUnaryOperator' for initializer " "true"

data class InitializerSuppressedAux(val first: Int, val second: Int)

fun initializerSuppressedOnInitializer(dc: InitializerSuppressedAux) {
    val (a, b) = InitializerSuppressedAux(-1, -1.<caret>inc())
}

// K1_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.WrapUnaryOperatorInspection
// K2_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.WrapUnaryOperatorInspection
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
// FUS_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
