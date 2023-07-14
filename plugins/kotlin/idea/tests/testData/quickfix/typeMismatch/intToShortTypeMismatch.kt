// "Change type of 'x' to 'Int'" "true"

fun foo() {
    val x: Short = <caret>100000
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix