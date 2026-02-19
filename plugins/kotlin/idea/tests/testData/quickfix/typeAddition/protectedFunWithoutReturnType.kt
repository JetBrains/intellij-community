// "Specify return type explicitly" "true"
package a

class A() {
    protected fun <caret>foo() = 1
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.SpecifyTypeExplicitlyIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.intentions.SpecifyTypeExplicitlyIntention