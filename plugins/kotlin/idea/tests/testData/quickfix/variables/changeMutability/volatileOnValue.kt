// "Change to 'var'" "true"
// WITH_STDLIB
// K2_ERROR: '@Volatile' annotation cannot be used on immutable properties.
class Foo {
    <caret>@Volatile
    val bar: String = ""
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix