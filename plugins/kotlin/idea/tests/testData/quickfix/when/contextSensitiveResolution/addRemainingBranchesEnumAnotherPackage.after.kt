// "Add remaining branches" "true"
// COMPILER_ARGUMENTS: -Xcontext-sensitive-resolution

package usage

import other.Color

fun test(c: Color) = when(c) {
    R -> TODO()
    G -> TODO()
    B -> TODO()
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddWhenRemainingBranchFixFactories$AddRemainingWhenBranchesQuickFix
