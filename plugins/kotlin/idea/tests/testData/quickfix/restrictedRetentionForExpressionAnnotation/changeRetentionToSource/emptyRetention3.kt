// "Change existing retention to SOURCE" "true"
class AnnotationRetention

<caret>@Retention()
@Target(AnnotationTarget.EXPRESSION)
annotation class Ann
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeRetentionToSourceFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeRetentionToSourceFix