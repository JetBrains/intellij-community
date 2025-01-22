// "Add SOURCE retention" "false"
// DISABLE_ERRORS
// ACTION: Change existing retention to SOURCE
// ACTION: Make internal
// ACTION: Make private
// ACTION: Remove EXPRESSION target
<caret>@Retention
@Target(AnnotationTarget.EXPRESSION)
annotation class Ann