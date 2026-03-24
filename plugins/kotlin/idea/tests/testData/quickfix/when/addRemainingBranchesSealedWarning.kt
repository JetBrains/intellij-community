// "Add remaining branches" "true"
// ERROR: Unresolved reference: TODO
// ERROR: Unresolved reference: TODO
// K2_ERROR: 'when' expression must be exhaustive. Add the 'is B', 'is C' branches or an 'else' branch.
sealed class Base {
    class A : Base()
    class B : Base()
    class C : Base()
}

fun test(base: Base, x: String?) {
    x ?: when<caret> (base) {
        is Base.A -> return
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddWhenRemainingBranchesFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddWhenRemainingBranchFixFactories$AddRemainingWhenBranchesQuickFix