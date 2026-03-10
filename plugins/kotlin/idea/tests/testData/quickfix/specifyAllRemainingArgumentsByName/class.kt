// "Specify all remaining arguments by name" "true"
// WITH_STDLIB
// K2_ERROR: No value passed for parameter 'a'.
class Foo(a: Int)

fun test() {
    Foo(<caret>)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix