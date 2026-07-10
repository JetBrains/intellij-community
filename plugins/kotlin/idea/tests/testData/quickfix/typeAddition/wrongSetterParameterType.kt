// "Change setter parameter type to Int" "true"
// K2_ERROR: WRONG_SETTER_PARAMETER_TYPE
class A() {
    var i: Int = 0
        set(v: <caret>Any) {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeAccessorTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeAccessorTypeFix