// "Add remaining branches with * import" "true"
// WITH_STDLIB
import Foo.*

enum class Foo {
    A, B, C
}

enum class Bar {
    A, B, C
}

class Test {
    fun foo(e: Foo) {
        when (e) {
            A -> TODO()
            B -> TODO()
            C -> TODO()
        }
    }
    fun bar(e: Bar) {
        when<caret> (e) {
        }
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddWhenRemainingBranchesFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.AddWhenRemainingBranchFixFactories$AddRemainingWhenBranchesQuickFix