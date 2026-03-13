// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB
// K2_ERROR: Argument type mismatch: actual type is 'A?', but 'A' was expected.

interface A

operator fun A?.plus(a: A?): A? = this

fun test(a1: A, a2: A) {
    notNull(<caret>a1 + a2)
}

fun notNull(t: A): A = t

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.WrapWithSafeLetCallFixFactories$WrapWithSafeLetCallModCommandAction