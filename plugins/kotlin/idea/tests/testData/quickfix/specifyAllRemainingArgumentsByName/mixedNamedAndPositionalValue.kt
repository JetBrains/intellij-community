// "Specify all remaining arguments by name" "false"
// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.3
// K2_AFTER_ERROR: MIXING_NAMED_AND_POSITIONAL_ARGUMENTS
// K2_AFTER_ERROR: NO_VALUE_FOR_PARAMETER
// K2_ERROR: MIXING_NAMED_AND_POSITIONAL_ARGUMENTS
// K2_ERROR: NO_VALUE_FOR_PARAMETER

context(name: String)
fun foo(value: Int) {}

fun bar() {
    foo(<caret>name = TODO("Provide String"), 0)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix