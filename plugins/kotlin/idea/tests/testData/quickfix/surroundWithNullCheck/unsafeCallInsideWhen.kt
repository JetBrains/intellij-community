// "Surround with null check" "true"
// ERROR: 'when' expression must be exhaustive, add necessary 'false' branch or 'else' branch instead

fun foo(arg: Int?, flag: Boolean) {
    when (flag) {
        true -> arg<caret>.hashCode()
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithNullCheckFix