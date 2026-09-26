// "Suppress 'KDocUnresolvedReference' for enum entry VALUE_A" "true"

enum class EnumEntrySuppressedOnEnumEntry {
    /**
     * [<caret>unresolved]
     */
    VALUE_A, VALUE_B
}

// K1_TOOL: org.jetbrains.kotlin.idea.k1.codeinsight.inspections.KDocUnresolvedReferenceInspection
// IGNORE_K2
