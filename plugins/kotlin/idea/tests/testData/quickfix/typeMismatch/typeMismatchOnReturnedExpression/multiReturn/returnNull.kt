// "Specify 'String?' return type for enclosing function 'test'" "true"
fun test() {
    if (true) return "foo"<caret>
    return null
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix