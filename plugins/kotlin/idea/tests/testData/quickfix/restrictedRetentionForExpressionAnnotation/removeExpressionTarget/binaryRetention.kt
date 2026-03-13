// "Remove EXPRESSION target" "true"
// K2_ERROR: Expression annotations with retention other than SOURCE are prohibited.
<caret>@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.EXPRESSION)
annotation class Ann
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveExpressionTargetFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveExpressionTargetFix