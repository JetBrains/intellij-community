// "Change return type of enclosing function 'foo' to 'Int'" "true"

fun foo(): String {
    return <caret>1
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing