// "Add SOURCE retention" "false"
// DISABLE-ERRORS
// ACTION: Change existing retention to SOURCE
// ACTION: Make internal
// ACTION: Make private
// ACTION: Remove EXPRESSION target
<caret>@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.EXPRESSION)
annotation class Ann