// "Replace '@JvmField' with 'const'" "true"
// WITH_STDLIB
<caret>@JvmField private val number: Int = 42
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceJvmFieldWithConstFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceJvmFieldWithConstFix