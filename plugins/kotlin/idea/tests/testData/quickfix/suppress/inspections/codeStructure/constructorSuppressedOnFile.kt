// "Suppress 'RemoveEmptySecondaryConstructorBody' for file ${file}" "true"

class ConstructorSuppressedOnFile() {
    constructor(p: Int): this() {<caret>}
}

// K1_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.RemoveEmptySecondaryConstructorBodyInspection
// K2_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.RemoveEmptySecondaryConstructorBodyInspection
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
// FUS_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
