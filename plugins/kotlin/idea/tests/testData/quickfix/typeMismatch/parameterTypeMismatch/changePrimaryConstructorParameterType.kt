// "Change parameter 'a' type of primary constructor of class 'B' to 'String'" "true"
class B(val a: Int)
fun foo() {
    B(if (true) ""<caret> else "")
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeParameterTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeParameterTypeFix