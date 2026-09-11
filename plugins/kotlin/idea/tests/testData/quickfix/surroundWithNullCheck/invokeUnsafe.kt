// "Surround with null check" "true"
// K2_ERROR: UNSAFE_IMPLICIT_INVOKE_CALL

operator fun Int.invoke() = this

fun foo(arg: Int?) {
    <caret>arg()
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SurroundWithNullCheckFixFactory$SurroundWithNullCheckFix