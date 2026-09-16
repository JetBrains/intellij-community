// "Specify return type explicitly" "true"
package a

class A() {
    protected fun <caret>foo() = 1
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsight.intentions.SpecifyTypeExplicitlyIntention