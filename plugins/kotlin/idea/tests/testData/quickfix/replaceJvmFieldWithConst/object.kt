// "Replace '@JvmField' with 'const'" "true"
// WITH_STDLIB
object Foo {
    <caret>@JvmField private val a = "Lorem ipsum"
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceJvmFieldWithConstFix