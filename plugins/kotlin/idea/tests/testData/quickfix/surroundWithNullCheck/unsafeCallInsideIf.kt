// "Surround with null check" "true"

fun foo(arg: Int?, flag: Boolean) {
    if (flag) arg<caret>.hashCode()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithNullCheckFix