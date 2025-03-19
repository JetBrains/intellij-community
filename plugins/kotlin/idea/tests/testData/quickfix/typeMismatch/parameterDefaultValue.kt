// "Change parameter 's' type of function 'foo' to 'Int'" "true"
val ONE = 1

fun foo(s: String = <caret>ONE + 1) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeParameterTypeFix
// IGNORE_K2