// "Replace 'emptyList()' with 'mutableListOf()'" "true"
// PRIORITY: HIGH
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: CANNOT_INFER_PARAMETER_TYPE

fun foo(list: MutableList<Int>?) {}

fun bar() {
    foo(emptyList<caret>())
}


// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ReplaceWithMutableCollectionFactoryFix
