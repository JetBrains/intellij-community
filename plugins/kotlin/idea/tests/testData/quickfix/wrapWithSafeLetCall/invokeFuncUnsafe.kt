// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB

fun foo(exec: (() -> Unit)?) {
    <caret>exec()
}