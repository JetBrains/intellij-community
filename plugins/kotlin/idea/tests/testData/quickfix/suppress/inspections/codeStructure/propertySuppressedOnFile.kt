// "Suppress 'CanBePrimaryConstructorProperty' for file ${file}" "true"

class PropertySuppressedOnFile(name: String) {
    val <caret>name = name
}

// K1_TOOL: org.jetbrains.kotlin.idea.inspections.CanBePrimaryConstructorPropertyInspection
// K2_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.CanBePrimaryConstructorPropertyInspection
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
// FUS_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
