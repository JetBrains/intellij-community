// "Remove 'in' modifier" "true"
interface A<T> {}

class B : A<<caret>in Int> {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase