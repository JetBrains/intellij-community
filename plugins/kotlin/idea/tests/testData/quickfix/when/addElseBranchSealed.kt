// "Add else branch" "true"
sealed class Base {
    class A : Base()
    class B : Base()
    class C : Base()
}

fun test(base: Base) {
    when<caret> (base) {
        is Base.A -> ""
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddWhenElseBranchFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddWhenElseBranchFix