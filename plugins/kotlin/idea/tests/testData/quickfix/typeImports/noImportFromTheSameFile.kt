// "Specify type explicitly" "true"

class A() {}

class B() {
    public val <caret>a = A()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.SpecifyTypeExplicitlyIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.intentions.SpecifyTypeExplicitlyIntention