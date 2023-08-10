// "Add remaining branches" "true"
// WITH_STDLIB

enum class FooEnum {
    A, B, `C`, `true`, `false`, `null`
}

fun test(foo: FooEnum?) = <caret>when (foo) {
    FooEnum.A -> "A"
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddWhenRemainingBranchesFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.AddWhenRemainingBranchFixFactories$AddRemainingWhenBranchesQuickFix