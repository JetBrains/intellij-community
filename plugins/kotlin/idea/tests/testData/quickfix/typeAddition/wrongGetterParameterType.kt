// "Change getter type to Int" "true"
class A() {
    val i: Int
        get(): <caret>Any = 1
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeAccessorTypeFixFactory$createAction$1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeAccessorTypeFixFactory$getFixes$1