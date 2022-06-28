// "Add SOURCE retention" "false"
// DISABLE-ERRORS
// ACTION: Change existent retention to SOURCE
// ACTION: Do not show return expression hints
// ACTION: Make internal
// ACTION: Make private
// ACTION: Remove EXPRESSION target
<caret>@Retention()
@Target(AnnotationTarget.EXPRESSION)
annotation class Ann