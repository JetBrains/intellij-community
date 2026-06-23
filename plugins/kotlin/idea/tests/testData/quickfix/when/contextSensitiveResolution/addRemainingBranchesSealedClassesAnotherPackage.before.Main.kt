// "Add remaining branches" "true"
// COMPILER_ARGUMENTS: -Xcontext-sensitive-resolution

package usage

import pkg.MyResult

fun handle(r: MyResult) = wh<caret>en (r) {
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddWhenRemainingBranchFixFactories$AddRemainingWhenBranchesQuickFix
