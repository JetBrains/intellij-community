// "Change existing retention to SOURCE" "true"
// K2_ERROR: RESTRICTED_RETENTION_FOR_EXPRESSION_ANNOTATION_ERROR
<caret>@Retention()
@Target(AnnotationTarget.EXPRESSION)
annotation class Ann
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeRetentionToSourceFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeRetentionToSourceFix