// "Wrap with []" "true"

annotation class Foo(val value: Array<String>)

@Foo(value = "abc"<caret>)
class Bar
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithArrayLiteralFix
/* IGNORE_K2 */