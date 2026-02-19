// "Add else branch" "true"
fun test() {
    val a = 12
    val x = wh<caret>en (a) {
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddWhenElseBranchFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddWhenElseBranchFix