// "Change return type of enclosing function 'test' to 'Any?'" "true"
// K2_ERROR: Return type mismatch: expected 'O', actual 'Any?'.
class O
class P

fun test(b: Boolean, p: P?): O {
    return when {
        b -> O()
        else -> p<caret>
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix