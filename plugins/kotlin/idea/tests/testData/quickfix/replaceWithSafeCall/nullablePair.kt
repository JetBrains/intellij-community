// "Replace with safe (?.) call" "true"
// WITH_STDLIB
// K2_ERROR: UNSAFE_OPERATOR_CALL

// IGNORE_K2
// TODO: Drop IGNORE_K2 when KT-87415 is fixed

fun f(nullable: Int?) {
    val pair: Pair<String, Int?> = "" to nullable <caret>/ 100
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithSafeCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix