// "Specify type explicitly" "true"

class My {
    val <caret>x
        get() = "abc"
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsight.intentions.SpecifyTypeExplicitlyIntention