// "Change type of 'x' to 'Long'" "true"

fun foo() {
    val x: Int = <caret>0L
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix