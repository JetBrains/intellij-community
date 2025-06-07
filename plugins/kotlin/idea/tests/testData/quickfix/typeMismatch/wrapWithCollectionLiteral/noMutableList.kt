// "class org.jetbrains.kotlin.idea.quickfix.WrapWithCollectionLiteralCallFix" "false"
// ERROR: Type mismatch: inferred type is String but MutableList<String> was expected
// K2_AFTER_ERROR: Argument type mismatch: actual type is 'String', but 'MutableList<String>' was expected.
// WITH_STDLIB

fun foo(a: String) {
    bar(a<caret>)
}

fun bar(a: MutableList<String>) {}
