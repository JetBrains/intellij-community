// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB

fun f(s: String, action: (String.() -> Unit)?) {
    s.action<caret>()
}