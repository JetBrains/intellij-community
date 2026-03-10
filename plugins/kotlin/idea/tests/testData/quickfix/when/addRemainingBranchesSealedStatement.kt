// "Add remaining branches" "true"
// ERROR: Unresolved reference: TODO
// ERROR: Unresolved reference: TODO
// ERROR: Unresolved reference: TODO
// K2_ERROR: 'when' expression must be exhaustive. Add the 'Another', 'is Something', 'null' branches or an 'else' branch.
sealed class Variant {
    object Singleton : Variant()

    class Something(val x: Int) : Variant()

    object Another : Variant()
}
fun test(v: Variant?) {
    wh<caret>en(v) {
        Variant.Singleton -> "s"
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddWhenRemainingBranchesFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddWhenRemainingBranchFixFactories$AddRemainingWhenBranchesQuickFix