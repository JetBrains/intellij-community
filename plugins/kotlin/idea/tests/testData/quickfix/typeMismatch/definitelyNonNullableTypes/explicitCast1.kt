// "Cast expression 'x' to 'T & Any'" "true"
// LANGUAGE_VERSION: 1.8
fun <T> foo(x: T & Any) {}

fun <T> bar(x: T) {
    foo(x<caret>)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CastExpressionFix