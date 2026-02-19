// "Change existing retention to SOURCE" "false"
// DISABLE_ERRORS
// ACTION: Add SOURCE retention
// ACTION: Make internal
// ACTION: Make private
// ACTION: Remove EXPRESSION target
<caret>@Target(AnnotationTarget.EXPRESSION)
annotation class Ann