// "Wrap element with 'setOf()' call" "true"
// WITH_STDLIB
// K2_ERROR: ASSIGNMENT_TYPE_MISMATCH

fun foo(a: String) {
    val s: Set<String>
    s = a<caret>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithCollectionLiteralCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithCollectionLiteralCallFix