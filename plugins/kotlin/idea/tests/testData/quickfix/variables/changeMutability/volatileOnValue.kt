// "Change to 'var'" "true"
// WITH_STDLIB
// K2_ERROR: VOLATILE_ON_VALUE
class Foo {
    <caret>@Volatile
    val bar: String = ""
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix