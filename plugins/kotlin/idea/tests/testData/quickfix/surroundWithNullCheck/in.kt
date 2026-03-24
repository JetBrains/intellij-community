// "Surround with null check" "true"
// K2_ERROR: Operator call is prohibited on a nullable receiver of type 'List<String>?'. Use '?.'-qualified call instead.
fun test(a: String, b: List<String>?) {
    a <caret>in b
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithNullCheckFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SurroundWithNullCheckFixFactory$SurroundWithNullCheckFix