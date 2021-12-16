// "class org.jetbrains.kotlin.idea.quickfix.CastExpressionFix" "false"
// LANGUAGE_VERSION: 1.7
// ERROR: Type mismatch: inferred type is T but T & Any was expected
fun <T> foo(x: T & Any) {}

fun <T> bar(x: T) {
    foo(x<caret>)
}
