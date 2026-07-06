// "Remove 'in' modifier" "true"
// K2_ERROR: CONFLICTING_PROJECTION
class Foo<out T> {}

fun bar(x : Foo<<caret>in Any>) {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase