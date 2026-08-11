// "Surround with null check" "true"
// ERROR: 'when' expression must be exhaustive, add necessary 'false' branch or 'else' branch instead
// K2_AFTER_ERROR: NO_ELSE_IN_WHEN
// K2_ERROR: NO_ELSE_IN_WHEN
// K2_ERROR: UNSAFE_CALL

fun foo(arg: Int?, flag: Boolean) {
    when (flag) {
        true -> arg<caret>.inc()
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithNullCheckFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SurroundWithNullCheckFixFactory$SurroundWithNullCheckFix