// "Change parameter 'a' type of function 'foo' to 'String'" "true"
// DISABLE_ERRORS
package test

open class B {
    open fun foo(a: Int) {}
}

class C : B() {
    override fun foo(a: Int) = super.foo(a)
}

fun test(b: B) {
    b.foo(<caret>"")
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeParameterTypeFix.ChangeParameterTypeFix