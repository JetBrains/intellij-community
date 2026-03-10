// "Add remaining branches" "true"
// WITH_STDLIB
// K2_ERROR: 'when' expression must be exhaustive. Add the 'B', 'C', '`true`', '`false`', '`null`', 'null' branches or an 'else' branch.

enum class FooEnum {
    A, B, `C`, `true`, `false`, `null`
}

fun test(foo: FooEnum?) = <caret>when (foo) {
    FooEnum.A -> "A"
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddWhenRemainingBranchesFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddWhenRemainingBranchFixFactories$AddRemainingWhenBranchesQuickFix