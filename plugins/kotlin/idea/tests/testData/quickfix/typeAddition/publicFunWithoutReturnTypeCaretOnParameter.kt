// "Specify return type explicitly" "true"
package a

class A() {
    public fun foo(<caret>p: String) = "a"
}
/* IGNORE_FIR */

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.SpecifyTypeExplicitlyIntention