// "Surround with null check" "true"

fun foo(arg: Int?) {
    arg<caret>.hashCode()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithNullCheckFix