// "Cast expression 'x' to 'Collection<T & Any>'" "true"
// LANGUAGE_VERSION: 1.8
fun <T> foo(x: Collection<T & Any>) {}

fun <T> bar(x: Collection<T>) {
    foo(x<caret>)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CastExpressionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.CastExpressionFixFactories$CastExpressionModCommandAction