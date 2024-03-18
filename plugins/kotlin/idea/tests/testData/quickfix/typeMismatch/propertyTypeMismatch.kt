// "Change type of 'f' to '(Long) -> Unit'" "true"
fun foo() {
    var f: Int = if (true) { x: Long ->  }<caret> else { x: Long ->  }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix