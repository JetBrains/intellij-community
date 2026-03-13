// "Change type of 'f' to '(Long) -> Unit'" "true"
// K2_ERROR: Argument type mismatch: actual type is '(Long) -> Unit', but 'Int' was expected.
// K2_ERROR: Initializer type mismatch: expected 'Int', actual '(Long) -> Unit'.
fun foo() {
    var f: Int =<caret> if (true) { x: Long ->  } else { x: Long ->  }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix