// "Specify all remaining arguments by name" "false"
// WITH_STDLIB
// WITH_JDK

fun test() {
    java.util.Base64.getMimeEncoder(<caret>)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix