// "class org.jetbrains.kotlin.idea.quickfix.CastExpressionFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.k2.codeinsight.fixes.CastExpressionFixFactories$CastExpressionModCommandAction" "false"
// ERROR: Type mismatch: inferred type is A but B was expected
// K2_AFTER_ERROR: Return type mismatch: expected 'B', actual 'A'.
open class A
class B : A()

fun foo(a: A): B {
    return a: A<caret>
}
