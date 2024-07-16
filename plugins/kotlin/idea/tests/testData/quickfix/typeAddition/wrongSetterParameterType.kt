// "Change setter parameter type to Int" "true"
class A() {
    var i: Int = 0
        set(v: <caret>Any) {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeAccessorTypeFixFactory$createAction$1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeAccessorTypeFixFactory$getFixes$1