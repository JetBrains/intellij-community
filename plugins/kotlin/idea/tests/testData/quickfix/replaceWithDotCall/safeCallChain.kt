// "Replace with dot call" "true"
class Foo(val bar: Bar)
class Bar(val baz: Baz)
class Baz(val qux: Int)

fun test(foo: Foo) {
    foo?<caret>.bar?.baz?.qux
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithDotCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithDotCallFix