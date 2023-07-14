// "Surround with null check" "true"

fun foo(arg: Int?) {
    42 + arg<caret>.hashCode() - 13
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithNullCheckFix