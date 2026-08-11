// "Add else branch" "true"
// K2_ERROR: NO_ELSE_IN_WHEN
fun foo(x: String?) {
    while (true) {
        x ?: when<caret> { true -> break }
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddWhenElseBranchFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddWhenElseBranchFix