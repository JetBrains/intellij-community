// "Cast expression 'Foo<Number>()' to 'Foo<Int>'" "true"
// K2_ERROR: RETURN_TYPE_MISMATCH
class Foo<out T>

fun foo(): Foo<Int> {
    return Foo<Number>()<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CastExpressionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.CastExpressionFixFactories$CastExpressionModCommandAction