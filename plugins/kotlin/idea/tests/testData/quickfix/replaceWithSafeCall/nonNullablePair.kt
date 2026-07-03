// "Replace with safe (?.) call" "true"
// WITH_STDLIB
// K2_ERROR: Operator call is prohibited on a nullable receiver of type 'Int?'. Use '?.'-qualified call instead.
fun f(nullable: Int?) {
    val pair: Pair<String, Int> = "" to nullable <caret>/ 100
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithSafeCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix