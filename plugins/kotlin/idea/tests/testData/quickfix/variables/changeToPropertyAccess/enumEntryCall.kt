// "Remove invocation" "true"
enum class Test {
    A
}

fun test() {
    Test.A<caret>()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToPropertyAccessFix