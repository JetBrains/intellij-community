// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB
// K2_ERROR: Reference has a nullable type '((() -> Unit) -> String)?'. Use explicit '?.invoke' to make a function-like call instead.

interface Foo {
    val f: ((() -> Unit) -> String)?
}

fun test(foo: Foo) {
    bar(foo.<caret>f {})
}

fun bar(s: String) {}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.WrapWithSafeLetCallFixFactories$WrapWithSafeLetCallModCommandAction