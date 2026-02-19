// "Add remaining branches" "true"
// ERROR: Unresolved reference: TODO
// ERROR: Unresolved reference: TODO

sealed class Base<U, V, W> {
    fun bar(it: TA) {
        <caret>when (it) {
            is Derived -> TODO()
        }
    }
}
class Derived<U, V>: Base<U, V, Int>()
class Generic<V, T>: Base<Int, V, String>()

typealias TA = Base<Double, Int, String>

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddWhenRemainingBranchesFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddWhenRemainingBranchFixFactories$AddRemainingWhenBranchesQuickFix