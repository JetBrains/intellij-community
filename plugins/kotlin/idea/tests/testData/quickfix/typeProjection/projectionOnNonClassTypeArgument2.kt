// "Remove 'in' modifier" "true"
class A<T> {}

class B {
    var foo = A<<caret>in Int>()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase