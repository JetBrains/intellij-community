// "Specify supertype" "true"
// K2_ERROR: Multiple supertypes available. Specify the intended supertype in angle brackets, e.g. 'super<Foo>'.
package a.b.c

interface X

open class Y {
    open fun foo() {}
}

interface Z {
    fun foo() {}
}

class Test : X, Y(), Z {
    override fun foo() {
        <caret>super.foo()
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifySuperTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SpecifySuperTypeFixFactory$SpecifySuperTypeQuickFix