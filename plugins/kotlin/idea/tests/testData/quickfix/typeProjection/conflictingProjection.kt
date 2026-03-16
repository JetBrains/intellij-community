// "Remove 'in' modifier" "true"
// K2_ERROR: Projection conflicts with variance of the corresponding type parameter of 'Foo<in Any>'. Remove the projection or replace it with '*'.
class Foo<out T> {}

fun bar(x : Foo<<caret>in Any>) {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase