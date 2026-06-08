// "Add remaining branches" "true"
// COMPILER_ARGUMENTS: -Xcontext-sensitive-resolution
// K2_ERROR: 'when' expression must be exhaustive. Add the 'Another', 'null' branches or an 'else' branch.

package test

sealed class Variant {
    object Singleton : Variant()
    object Another : Variant()
}

fun test(v: Variant?) = wh<caret>en(v) {
    Variant.Singleton -> "s"
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddWhenRemainingBranchFixFactories$AddRemainingWhenBranchesQuickFix
