// "Change existing retention to SOURCE" "true"
// K2_ERROR: Expression annotations with retention other than SOURCE are prohibited.
<caret>@Retention()
@Target(AnnotationTarget.EXPRESSION)
annotation class Ann
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeRetentionToSourceFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeRetentionToSourceFix