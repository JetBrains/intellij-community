// "Change existing retention to SOURCE" "true"
<caret>@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.EXPRESSION)
annotation class Ann
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeRetentionToSourceFix