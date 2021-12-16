// "class org.jetbrains.kotlin.idea.quickfix.CastExpressionFix" "false"
// LANGUAGE_VERSION: 1.7
// ERROR: Type mismatch: inferred type is Collection<T> but Collection<T & Any> was expected
fun <T> foo(x: Collection<T & Any>) {}

fun <T> bar(x: Collection<T>) {
    foo(x<caret>)
}
