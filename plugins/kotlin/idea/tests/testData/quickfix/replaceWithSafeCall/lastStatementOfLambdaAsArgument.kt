// "Replace with safe (?.) call" "true"
// DISABLE-ERRORS
fun test(foo: Foo?) {
    baz {
        bar("")
        bar("")
        foo<caret>.s
    }
}

class Foo {
    val s = ""
}

fun bar(s: String) {}

fun baz(f: () -> String) {
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithSafeCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithSafeCallFix