// "Change getter type to (String) -> Int" "true"
class A {
    val x: (String) -> Int
        get(): Int<caret> = {42}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeAccessorTypeFixFactory$createAction$1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeAccessorTypeFixFactory$getFixes$1