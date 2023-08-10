// "Surround with null check" "true"

operator fun Int.invoke() = this

fun foo(arg: Int?) {
    <caret>arg()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithNullCheckFix