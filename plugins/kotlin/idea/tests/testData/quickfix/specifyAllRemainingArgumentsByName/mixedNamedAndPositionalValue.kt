// "Specify all remaining arguments by name" "false"
// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.3
// K2_ERROR: Mixing named and positional arguments is not allowed unless the order of the arguments matches the order of the parameters.
// K2_ERROR: No value passed for parameter 'value'.
// K2_AFTER_ERROR: Mixing named and positional arguments is not allowed unless the order of the arguments matches the order of the parameters.
// K2_AFTER_ERROR: No value passed for parameter 'value'.

context(name: String)
fun foo(value: Int) {}

fun bar() {
    foo(<caret>name = TODO("Provide String"), 0)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix