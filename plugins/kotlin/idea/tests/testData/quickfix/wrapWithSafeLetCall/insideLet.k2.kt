// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB
// K2_ERROR: Argument type mismatch: actual type is 'String?', but 'String' was expected.

fun foo(x: String?, y: String) {
    y.let { bar(<caret>x, it) }
}

fun bar(s: String, t: String) = s.hashCode() + t.hashCode()

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.WrapWithSafeLetCallFixFactories$WrapWithSafeLetCallModCommandAction