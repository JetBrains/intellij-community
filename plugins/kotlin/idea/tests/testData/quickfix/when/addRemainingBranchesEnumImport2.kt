// "Add remaining branches with * import" "true"
// WITH_STDLIB
// K2_ERROR: 'when' expression must be exhaustive. Add the 'C' branch or an 'else' branch.
import Foo.*

enum class Foo {
    A, B, C
}

class Test {
    fun foo(e: Foo) {
        when<caret> (e) {
            A, Foo.B -> TODO()
        }
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddWhenRemainingBranchesFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddWhenRemainingBranchFixFactories$AddRemainingWhenBranchesQuickFix