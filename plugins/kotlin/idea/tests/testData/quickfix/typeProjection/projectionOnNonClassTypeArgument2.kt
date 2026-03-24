// "Remove 'in' modifier" "true"
// K2_ERROR: Projections are not allowed on type arguments of functions calls.
class A<T> {}

class B {
    var foo = A<<caret>in Int>()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase