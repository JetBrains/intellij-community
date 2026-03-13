// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB
// K2_ERROR: Argument type mismatch: actual type is 'Int?', but 'Int' was expected.

object Obj {
    fun foo(x: Int) = x
}
val arg: Int? = null
val argFoo = Obj.foo(<caret>arg)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithSafeLetCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.WrapWithSafeLetCallFixFactories$WrapWithSafeLetCallModCommandAction