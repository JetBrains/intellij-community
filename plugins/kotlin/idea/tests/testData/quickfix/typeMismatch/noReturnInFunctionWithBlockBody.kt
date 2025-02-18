// "Remove explicitly specified return type of enclosing function 'foo'" "true"
fun foo(): Int {
<caret>}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing
// IGNORE_K2
// For K2, see KTIJ-33125