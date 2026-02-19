// "Replace with dot call" "true"
// LANGUAGE_VERSION: 1.6
// SHOULD_BE_AVAILABLE_AFTER_EXECUTION
// Note: quick fix is available after the execution due to a separate warning (SAFE_CALL_WILL_CHANGE_NULLABILITY)
class Foo(val bar: Bar)
class Bar(val baz: Baz)
class Baz(val qux: Int)

fun test(foo: Foo) {
    foo?.bar?<caret>.baz?.qux
}
// IGNORE_K2
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithDotCallFix