// "Remove 'in' modifier" "true"
// K2_ERROR: PROJECTION_IN_IMMEDIATE_ARGUMENT_TO_SUPERTYPE
interface A<T> {}

class B : A<<caret>in Int> {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase