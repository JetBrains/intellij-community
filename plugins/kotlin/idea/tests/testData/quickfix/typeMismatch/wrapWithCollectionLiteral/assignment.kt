// "Wrap element with 'setOf()' call" "true"
// WITH_STDLIB
// K2_ERROR: Assignment type mismatch: actual type is 'String', but 'Set<String>' was expected.

fun foo(a: String) {
    val s: Set<String>
    s = a<caret>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithCollectionLiteralCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithCollectionLiteralCallFix