// "Remove 'in' modifier" "true"
class Foo<out T> {}

fun bar(x : Foo<<caret>in Any>) {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase