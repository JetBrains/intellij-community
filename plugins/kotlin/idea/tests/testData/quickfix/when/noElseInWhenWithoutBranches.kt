// "Add else branch" "true"
// K2_ERROR: 'when' expression must be exhaustive. Add an 'else' branch.
fun test() {
    val a = 12
    val x = wh<caret>en (a) {
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddWhenElseBranchFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddWhenElseBranchFix