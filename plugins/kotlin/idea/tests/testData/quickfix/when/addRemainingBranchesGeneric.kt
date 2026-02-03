// "Add remaining branches" "true"
// ERROR: Unresolved reference: TODO
// ERROR: Unresolved reference: TODO

sealed class Base<U, V, W> {
    fun foo(it: Base<U, V, W>) {
        <caret>when (it) {
            is Derived -> TODO()
        }
    }
}

class Derived<U, V>: Base<U, V, Int>()
class Generic<V, T>: Base<Int, V, String>()

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddWhenRemainingBranchesFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddWhenRemainingBranchFixFactories$AddRemainingWhenBranchesQuickFix