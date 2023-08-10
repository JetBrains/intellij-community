// "Change type of 't' to 'T'" "true"
interface T

fun foo() {
    val t: Int = <caret>object: T{}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix