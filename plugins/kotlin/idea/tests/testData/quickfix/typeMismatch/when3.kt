// "Change type of 's' to 'String'" "true"
fun test(i: Int) {
    val s: Int = when (i) {
        0 -> ""<caret>
        else -> ""
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix