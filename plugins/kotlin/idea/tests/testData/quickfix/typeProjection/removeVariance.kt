// "Remove 'out' modifier" "true"
// K2_ERROR: Variance annotations are only allowed for type parameters of classes and interfaces.
fun <out<caret> String> foo() { }
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase