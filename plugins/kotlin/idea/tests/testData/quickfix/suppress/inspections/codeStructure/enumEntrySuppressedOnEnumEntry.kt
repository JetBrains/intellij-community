// "Suppress 'KDocUnresolvedReference' for enum entry VALUE_A" "true"

enum class EnumEntrySuppressedOnEnumEntry {
    /**
     * [<caret>unresolved]
     */
    VALUE_A, VALUE_B
}

// K1_TOOL: org.jetbrains.kotlin.idea.k1.codeinsight.inspections.KDocUnresolvedReferenceInspection
// K2_TOOL: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.KDocUnresolvedReferenceInspection
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
// FUS_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
