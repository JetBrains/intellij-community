// "Add remaining branches" "true"
// WITH_STDLIB

sealed class A
class B : A()

fun test(a: A) {
  val i = w<caret>hen (a)

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddWhenRemainingBranchFixFactories$AddRemainingWhenBranchesQuickFix