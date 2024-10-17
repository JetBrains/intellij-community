// "Remove EXPRESSION target" "true"
<caret>@Retention
@Target(AnnotationTarget.FIELD, AnnotationTarget.EXPRESSION, AnnotationTarget.PROPERTY)
annotation class Ann
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveExpressionTargetFix