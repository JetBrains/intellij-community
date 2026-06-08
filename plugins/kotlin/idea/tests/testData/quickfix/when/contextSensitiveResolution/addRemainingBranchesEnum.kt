// "Add remaining branches" "true"
// COMPILER_ARGUMENTS: -Xcontext-sensitive-resolution
// K2_ERROR: 'when' expression must be exhaustive. Add the 'R', 'G' branches or an 'else' branch.

package test

enum class Color { R, G, B }

fun test(c: Color) = wh<caret>en(c) {
    Color.B -> 0xff
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddWhenRemainingBranchFixFactories$AddRemainingWhenBranchesQuickFix
