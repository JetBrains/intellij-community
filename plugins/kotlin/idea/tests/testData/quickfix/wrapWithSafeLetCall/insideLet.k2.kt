// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB
// K2_ERROR: ARGUMENT_TYPE_MISMATCH

fun foo(x: String?, y: String) {
    y.let { bar(<caret>x, it) }
}

fun bar(s: String, t: String) = s.hashCode() + t.hashCode()

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.WrapWithSafeLetCallFixFactories$WrapWithSafeLetCallModCommandAction