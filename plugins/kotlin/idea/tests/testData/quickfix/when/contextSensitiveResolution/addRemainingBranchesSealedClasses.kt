// "Add remaining branches" "true"
// COMPILER_ARGUMENTS: -Xcontext-sensitive-resolution
// K2_ERROR: 'when' expression must be exhaustive. Add the 'is Err' branch or an 'else' branch.

package test

sealed class MyResult {
    class Ok(val v: String) : MyResult()
    class Err(val m: String) : MyResult()
}

fun handle(r: MyResult): String = wh<caret>en (r) {
    is MyResult.Ok -> "ok"
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddWhenRemainingBranchFixFactories$AddRemainingWhenBranchesQuickFix
