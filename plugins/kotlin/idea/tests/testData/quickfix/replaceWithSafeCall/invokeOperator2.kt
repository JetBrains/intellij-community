// "Replace with safe (?.) call" "true"
// WITH_STDLIB
// K2_ERROR: FUNCTION_EXPECTED
class Foo(val bar: Bar)

class Bar {
    operator fun invoke() {}
}

fun test(foo: Foo?) {
    foo<caret>.bar()
}
// IGNORE_K2
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix