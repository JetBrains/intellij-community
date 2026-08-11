// "Add remaining branches" "true"
// COMPILER_ARGUMENTS: -Xcontext-sensitive-resolution
// K2_ERROR: NO_ELSE_IN_WHEN

package test

sealed class MyResult {
    class Ok(val v: String) : MyResult()
    class Err(val m: String) : MyResult()
}

fun handle(r: MyResult): String = wh<caret>en (r) {
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddWhenRemainingBranchFixFactories$AddRemainingWhenBranchesQuickFix
