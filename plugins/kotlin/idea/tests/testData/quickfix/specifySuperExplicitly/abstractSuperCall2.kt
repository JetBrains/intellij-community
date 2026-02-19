// "Specify super type 'I' explicitly" "true"

interface I {
    fun foo(): String = "default"
}

abstract class A {
    abstract fun foo(): String
}

class B : A(), I {
    override fun foo(): String = super<A>.<caret>foo()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AbstractSuperCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SpecifySuperTypeExplicitlyFix