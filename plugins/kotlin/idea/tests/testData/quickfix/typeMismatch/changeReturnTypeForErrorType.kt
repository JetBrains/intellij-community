// "Change return type of enclosing function 'test' to 'Any'" "true"
// ERROR: Unresolved reference: unknownVariable

fun test(): Int {
    if (true) {
        if (true) {
            return 10
        } else {
            return unknownVariable
        }
    } else {
        return fal<caret>se
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix