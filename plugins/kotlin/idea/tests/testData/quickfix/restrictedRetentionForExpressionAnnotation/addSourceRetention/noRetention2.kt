// "Add SOURCE retention" "true"
annotation class Retention

@Retention
<caret>@Target(AnnotationTarget.EXPRESSION)
annotation class Ann
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddSourceRetentionFix