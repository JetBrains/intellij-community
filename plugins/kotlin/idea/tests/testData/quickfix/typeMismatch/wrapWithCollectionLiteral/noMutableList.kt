// "class org.jetbrains.kotlin.idea.quickfix.WrapWithCollectionLiteralCallFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.quickfix.WrapWithCollectionLiteralCallFix" "false"
// ERROR: Type mismatch: inferred type is String but MutableList<String> was expected
// WITH_STDLIB
// K2_AFTER_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: ARGUMENT_TYPE_MISMATCH

fun foo(a: String) {
    bar(a<caret>)
}

fun bar(a: MutableList<String>) {}
