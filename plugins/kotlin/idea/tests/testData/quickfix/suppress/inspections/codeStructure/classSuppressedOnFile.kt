// "Suppress 'EqualsOrHashCode' for file ${file}" "true"

class <caret>ClassSuppressedOnFile {
    override fun equals(other: Any?) = true
}

// K1_TOOL: org.jetbrains.kotlin.idea.inspections.EqualsOrHashCodeInspection
// K2_TOOL: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.EqualsOrHashCodeInspection
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
// FUS_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
