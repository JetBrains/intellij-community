// "Suppress 'DIVISION_BY_ZERO' for enum entry A" "true"

enum class E {
    A {
        fun foo() = 2 / <caret>0
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction