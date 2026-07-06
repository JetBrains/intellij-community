// "Replace '@JvmField' with 'const'" "true"
// WITH_STDLIB
// K2_ERROR: INAPPLICABLE_JVM_FIELD
object Foo {
    <caret>@JvmField private val a = "Lorem ipsum"
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceJvmFieldWithConstFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceJvmFieldWithConstFix