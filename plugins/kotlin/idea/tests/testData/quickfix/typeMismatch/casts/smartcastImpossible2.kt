// "Cast expression 'a' to 'Foo'" "true"

interface Foo {
    operator fun not() : Foo
}

open class MyClass {
    public open val a: Any = "42"
}

fun MyClass.foo(): Any {
    if (a is Foo) {
        return !a<caret>
    }
    return 42
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CastExpressionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.CastExpressionFixFactories$applicator$1