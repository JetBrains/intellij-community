// "Remove EXPRESSION target" "true"
<caret>@Retention
@Target(AnnotationTarget.EXPRESSION)
annotation class Ann
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveExpressionTargetFix