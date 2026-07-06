// "Cast expression 'x' to 'Foo<*>'" "true"
// K2_ERROR: SMARTCAST_IMPOSSIBLE

interface Foo<T: Number> {
    fun bar()
}

open class MyClass {
    public open val x: Any = "42"
}

fun MyClass.bar() {
    if (x is Foo<*>) {
        x<caret>.bar()
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CastExpressionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.CastExpressionFixFactories$CastExpressionModCommandAction