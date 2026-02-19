// "Change getter type to (String) -> Int" "true"
class A {
    val x: (String) -> Int
        get(): Int<caret> = {42}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeAccessorTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeAccessorTypeFix