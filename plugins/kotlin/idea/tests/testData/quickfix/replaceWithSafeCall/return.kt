// "Replace with safe (?.) call" "true"
fun test(foo: Foo?): String {
    return foo<caret>.s
}

class Foo {
    val s = ""
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithSafeCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithSafeCallFix