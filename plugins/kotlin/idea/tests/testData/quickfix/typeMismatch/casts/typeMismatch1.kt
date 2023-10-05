// "Cast expression 'Foo<Number>()' to 'Foo<Int>'" "true"
class Foo<out T>

fun foo(): Foo<Int> {
    return Foo<Number>()<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CastExpressionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.CastExpressionFixFactories$applicator$1