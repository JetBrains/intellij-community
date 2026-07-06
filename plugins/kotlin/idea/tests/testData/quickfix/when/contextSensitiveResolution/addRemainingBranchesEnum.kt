// "Add remaining branches" "true"
// COMPILER_ARGUMENTS: -Xcontext-sensitive-resolution
// K2_ERROR: NO_ELSE_IN_WHEN

package test

enum class Color { R, G, B }

fun test(c: Color) = wh<caret>en(c) {
    Color.B -> 0xff
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddWhenRemainingBranchFixFactories$AddRemainingWhenBranchesQuickFix
