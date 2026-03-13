// "Change getter type to (String) -> Int" "true"
// K2_ERROR: Getter return type must be equal to the type of the property, i.e. '(String) -> Int'.
// K2_ERROR: Return type mismatch: expected 'Int', actual '() -> Int'.
class A {
    val x: (String) -> Int
        get(): Int<caret> = {42}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeAccessorTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeAccessorTypeFix