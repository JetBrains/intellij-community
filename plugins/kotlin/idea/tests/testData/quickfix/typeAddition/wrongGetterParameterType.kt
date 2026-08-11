// "Change getter type to Int" "true"
// K2_ERROR: WRONG_GETTER_RETURN_TYPE
class A() {
    val i: Int
        get(): <caret>Any = 1
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeAccessorTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeAccessorTypeFix