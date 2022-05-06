// "Change existent retention to SOURCE" "false"
// DISABLE-ERRORS
// ACTION: Add SOURCE retention
// ACTION: Do not show return expression hints
// ACTION: Make internal
// ACTION: Make private
// ACTION: Remove EXPRESSION target
<caret>@Target(AnnotationTarget.EXPRESSION)
annotation class Ann