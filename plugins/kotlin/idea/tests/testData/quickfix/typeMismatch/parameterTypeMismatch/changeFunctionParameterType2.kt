// "Change parameter 'y' type of function 'foo' to 'String'" "true"
fun foo(v: Int, w: Int = 0, x: Int = 0, y: Int, z: (Int) -> Int = {42}) {
    foo(0, 1, y = ""<caret>)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeParameterTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeParameterTypeFix