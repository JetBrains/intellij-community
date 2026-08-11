// "Specify all remaining arguments by name" "true"
// WITH_STDLIB
// LANGUAGE_VERSION: 2.4
// K2_ERROR: NONE_APPLICABLE
context(l: String)
fun ctxFun() {}

context(_: String)
fun myFun() {
    <caret>context() {
        ctxFun()
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix