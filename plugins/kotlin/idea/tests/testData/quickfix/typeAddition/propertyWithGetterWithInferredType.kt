// "Specify type explicitly" "true"

class My {
    val <caret>x
        get() = "abc"
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.SpecifyTypeExplicitlyIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.intentions.SpecifyTypeExplicitlyIntention