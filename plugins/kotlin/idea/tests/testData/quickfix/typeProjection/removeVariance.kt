// "Remove 'out' modifier" "true"
// K2_ERROR: VARIANCE_ON_TYPE_PARAMETER_NOT_ALLOWED
fun <out<caret> String> foo() { }
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase