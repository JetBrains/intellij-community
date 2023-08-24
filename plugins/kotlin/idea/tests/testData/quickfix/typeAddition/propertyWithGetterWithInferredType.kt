// "Specify type explicitly" "true"

class My {
    val <caret>x
        get() = "abc"
}
/* IGNORE_FIR */

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.SpecifyTypeExplicitlyIntention