// "Remove invocation" "true"
object Test

fun test() {
    Test<caret>()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToPropertyAccessFix