// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB

interface Foo {
    val f: ((() -> Unit) -> String)?
}

fun test(foo: Foo) {
    bar(foo.<caret>f {})
}

fun bar(s: String) {}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.WrapWithSafeLetCallFixFactories$applicator$1