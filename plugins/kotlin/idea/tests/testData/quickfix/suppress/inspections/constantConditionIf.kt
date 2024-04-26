// "Suppress 'ConstantConditionIf' for fun foo" "true"

fun foo() {
    if (<caret>true) {
    }
}

// IGNORE_K2
// TOOL: org.jetbrains.kotlin.idea.inspections.ConstantConditionIfInspection
// FUS_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix