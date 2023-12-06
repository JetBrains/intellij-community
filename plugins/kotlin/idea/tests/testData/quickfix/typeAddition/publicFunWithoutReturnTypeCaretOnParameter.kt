// "Specify return type explicitly" "true"
package a

class A() {
    public fun foo(<caret>p: String) = "a"
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.SpecifyTypeExplicitlyIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.intentions.SpecifyTypeExplicitlyIntention