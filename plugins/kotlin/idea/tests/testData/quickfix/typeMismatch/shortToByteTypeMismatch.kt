// "Change type of 'x' to 'Int'" "true"

fun foo() {
    val x: Byte = <caret>1000
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix