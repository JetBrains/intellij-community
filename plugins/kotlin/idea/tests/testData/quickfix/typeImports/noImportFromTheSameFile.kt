// "Specify type explicitly" "true"

class A() {}

class B() {
    public val <caret>a = A()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.SpecifyTypeExplicitlyIntention