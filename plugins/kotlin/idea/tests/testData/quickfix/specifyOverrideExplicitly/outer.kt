// "Specify override for 'foo(): Unit' explicitly" "true"

interface A {
    fun foo()
}

open class B : A {
    override fun foo() {}
}

fun bar(a: A) {
    class C<caret> : B(), A by a
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyOverrideExplicitlyFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SpecifyOverrideExplicitlyFixFactory$SpecifyOverrideExplicitlyFix