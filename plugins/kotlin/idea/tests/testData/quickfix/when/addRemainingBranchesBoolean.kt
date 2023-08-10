// "Add remaining branches" "true"
// ERROR: Unresolved reference: TODO
fun test(b: Boolean) = wh<caret>en(b) {
    false -> 0
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddWhenRemainingBranchesFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.AddWhenRemainingBranchFixFactories$AddRemainingWhenBranchesQuickFix