// "Add remaining branches" "true"
// ERROR: Unresolved reference: TODO
// ERROR: Unresolved reference: TODO
enum class Color { R, G, B }
fun use(c: Color) {
    <caret>when (c) {
        Color.R -> red()
    }
}

fun red() {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddWhenRemainingBranchesFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.AddWhenRemainingBranchFixFactories$AddRemainingWhenBranchesQuickFix