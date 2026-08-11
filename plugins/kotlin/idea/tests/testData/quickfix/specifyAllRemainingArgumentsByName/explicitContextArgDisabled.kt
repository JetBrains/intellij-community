// "Specify all remaining arguments by name" "false"
// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters -XXLanguage:-ExplicitContextArguments
// DISABLE_K2_ERRORS

context(name: String)
fun foo(value: Int) {}

fun bar() {
    foo(<caret>name = TODO("Provide String"), 0)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix