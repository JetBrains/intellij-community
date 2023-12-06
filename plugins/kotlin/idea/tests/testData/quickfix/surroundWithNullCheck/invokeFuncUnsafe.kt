// "Surround with null check" "true"

fun foo(exec: (() -> Unit)?) {
    <caret>exec()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithNullCheckFix