// "Specify return type explicitly" "true"
package a

class A() {
    public fun <caret>foo() = "a"
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsight.intentions.SpecifyTypeExplicitlyIntention