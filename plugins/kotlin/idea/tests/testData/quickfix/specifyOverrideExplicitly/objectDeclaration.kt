// "Specify override for 'foo(): Unit' explicitly" "true"
interface A {
    fun foo()
}

open class B : A {
    override fun foo() {}
}

object Obj : A {
    override fun foo() {}
}

class<caret> Derived : B(), A by Obj
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyOverrideExplicitlyFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SpecifyOverrideExplicitlyFixFactory$SpecifyOverrideExplicitlyFix