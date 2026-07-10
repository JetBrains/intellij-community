// "Remove 'in' modifier" "true"
// K2_ERROR: PROJECTION_ON_NON_CLASS_TYPE_ARGUMENT
class A<T> {}

class B {
    var foo = A<<caret>in Int>()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase