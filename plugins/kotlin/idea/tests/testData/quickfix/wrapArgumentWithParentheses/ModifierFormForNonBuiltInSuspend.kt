// "Wrap argument with parentheses" "true"
infix fun Int.suspend(bar: () -> Unit) {}

fun foo() {
    1 suspend<caret> {}
}
