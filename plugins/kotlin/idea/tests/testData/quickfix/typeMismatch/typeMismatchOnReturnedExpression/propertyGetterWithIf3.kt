// "Change type of 'foo' to 'Any'" "true"
class O
class P

val foo: O
    get() {
        if (true) return O() else return P()<caret>
    }


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix