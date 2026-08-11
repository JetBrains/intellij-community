// "Add remaining branches" "true"
// COMPILER_ARGUMENTS: -Xcontext-sensitive-resolution

package usage

import pkg.MyResult

fun handle(r: MyResult) = wh<selection><caret></selection>en (r) {
    is Err -> TODO()
    is Ok -> TODO()
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddWhenRemainingBranchFixFactories$AddRemainingWhenBranchesQuickFix
