// "Suppress 'ConstantConditionIf' for fun foo" "true"

fun foo() {
    if (<caret>true) {
    }
}

// IGNORE_FIR
// TOOL: org.jetbrains.kotlin.idea.inspections.ConstantConditionIfInspection
// FUS_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix