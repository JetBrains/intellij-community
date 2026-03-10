// "Add remaining branches" "true"
// ERROR: Unresolved reference: TODO
// ERROR: Unresolved reference: TODO
// K2_ERROR: 'when' expression must be exhaustive. Add the 'G', 'B' branches or an 'else' branch.
enum class Color { R, G, B }
fun use(c: Color) {
    <caret>when (c) {
        Color.R -> red()
    }
}

fun red() {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddWhenRemainingBranchesFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddWhenRemainingBranchFixFactories$AddRemainingWhenBranchesQuickFix