// "Suppress 'ConstantConditionIf' for fun foo" "true"

fun foo() {
    if (<caret>true) {
    }
}

// IGNORE_FIR
// TOOL: org.jetbrains.kotlin.idea.inspections.ConstantConditionIfInspection