// "Change type of 'x' to 'String?'" "true"
fun foo(condition: Boolean) {
    var x = null
    if (condition) {
        x = "abc"<caret>
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix