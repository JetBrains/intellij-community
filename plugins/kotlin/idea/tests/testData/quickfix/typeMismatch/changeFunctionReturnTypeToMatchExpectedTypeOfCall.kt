// "Change return type of called function 'bar' to 'String'" "true"
fun bar(): Any = ""
fun foo(): String = bar(<caret>)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForCalled