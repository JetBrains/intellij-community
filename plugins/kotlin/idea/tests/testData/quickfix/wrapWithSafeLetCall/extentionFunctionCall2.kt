// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB

fun f(s: String, action: (String.() -> Unit)?) {
    s.foo().bar().action<caret>()
}

fun String.foo() = ""

fun String.bar() = ""