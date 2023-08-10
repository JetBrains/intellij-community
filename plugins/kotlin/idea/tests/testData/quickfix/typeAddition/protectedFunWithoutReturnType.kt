// "Specify return type explicitly" "true"
package a

class A() {
    protected fun <caret>foo() = 1
}
/* IGNORE_FIR */

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.SpecifyTypeExplicitlyIntention