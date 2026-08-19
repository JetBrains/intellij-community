// "Specify all remaining arguments by name" "true"
// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:-ExplicitContextArguments
fun foo(a: Int = 5, b: Int = 5) {}

fun test() {
    foo<caret>()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsight.intentions.SpecifyAllRemainingArgumentsByNameIntention