// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB

interface A

operator fun A?.plus(a: A?): A? = this

fun test(a1: A, a2: A) {
    notNull(<caret>a1 + a2)
}

fun notNull(t: A): A = t

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.WrapWithSafeLetCallFixFactories$applicator$1