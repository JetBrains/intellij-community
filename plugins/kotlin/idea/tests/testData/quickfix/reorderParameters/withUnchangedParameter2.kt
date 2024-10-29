// "Reorder parameters" "true"
fun foo(
    a: String,
    b: String = d<caret>,
    c: String,
    d: String,
    f: String,
) {}

fun main() {
    foo("a", "b", "c", "d", "e")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReorderParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ReorderParametersFixFactory$ReorderParametersFix