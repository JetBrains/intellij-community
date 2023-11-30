// "Cast expression 'a + a' to 'B'" "true"
interface A {
    operator fun plus(x: Any): A
}
interface B : A

fun foo(a: A): B {
    return a + a<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CastExpressionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.CastExpressionFixFactories$applicator$1