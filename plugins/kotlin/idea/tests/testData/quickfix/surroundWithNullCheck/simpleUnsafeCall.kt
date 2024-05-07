// "Surround with null check" "true"

fun foo(arg: Int?) {
    arg<caret>.inc()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithNullCheckFix