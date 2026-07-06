// "Add else branch" "true"
// K2_ERROR: NO_ELSE_IN_WHEN
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

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddWhenElseBranchFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddWhenElseBranchFix