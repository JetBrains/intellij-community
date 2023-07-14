// "Specify type explicitly" "true"

val <caret>x
    get(): Int = 42
/* IGNORE_FIR */

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.SpecifyTypeExplicitlyIntention