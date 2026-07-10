// "Add remaining branches" "true"
// WITH_STDLIB
// K2_ERROR: NO_ELSE_IN_WHEN

sealed class FooSealed
object A: FooSealed()
object B: FooSealed()
object `C`: FooSealed()
class D: FooSealed()
class `true`: FooSealed()
class `false`: FooSealed()
object `null`: FooSealed()

fun test(foo: FooSealed?) = <caret>when (foo) {
    A -> "A"
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddWhenRemainingBranchesFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddWhenRemainingBranchFixFactories$AddRemainingWhenBranchesQuickFix