// "Specify type explicitly" "true"
// ACTION: Add getter
// ACTION: Convert property initializer to getter
// ACTION: Convert property to function
// ACTION: Convert to lazy property
// ACTION: Introduce backing property
// ACTION: Move to companion object
// ACTION: Specify type explicitly

class A() {
    public val <caret>t = hashCode()
}
/* IGNORE_FIR */

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.SpecifyTypeExplicitlyIntention