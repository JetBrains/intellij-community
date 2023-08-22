// "Surround with null check" "true"

infix fun Int.op(arg: Int) = this

fun foo(arg: Int?) {
    arg <caret>op 42
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithNullCheckFix