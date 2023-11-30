// "Change type of 'complex' to '(Int) -> Long'" "true"

val complex: (Int) -> String
    get() = { it.toLong()<caret> }
/* IGNORE_K2 */

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing