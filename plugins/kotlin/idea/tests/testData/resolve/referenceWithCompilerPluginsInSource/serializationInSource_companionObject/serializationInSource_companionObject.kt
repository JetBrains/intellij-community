package test

fun usage() {
    <caret>FooBar.serializer()
}

// REF: (test).FooBar
// SKIP_IS_REFERENCE_TO_CHECK
