// "Specify override for 'foo(): Unit' explicitly" "true"

interface A {
    fun foo()
}

open class B : A {
    override fun foo() {}
}

class C<caret>(val a: A) : B(), A by a
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyOverrideExplicitlyFix