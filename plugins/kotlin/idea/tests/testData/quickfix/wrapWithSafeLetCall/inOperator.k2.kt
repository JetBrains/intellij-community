// "Wrap with '?.let { ... }' call" "true"
// ACTION: Add non-null asserted (l!!) call
// ACTION: Replace overloaded operator with function call
// ACTION: Replace with safe (?.) call
// ACTION: Surround with null check
// K2_ERROR: Operator call is prohibited on a nullable receiver of type 'List<String>?'. Use '?.'-qualified call instead.

fun test(l: List<String>?, s: String) {
    if (s <caret>in l) {}
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.WrapWithSafeLetCallFixFactories$WrapWithSafeLetCallModCommandAction