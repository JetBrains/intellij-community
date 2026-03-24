// "Replace 'emptyList()' with 'mutableListOf()'" "true"
// PRIORITY: HIGH
// K2_ERROR: Argument type mismatch: actual type is 'List<uninferred T (of fun <T> emptyList)>', but 'MutableList<Int>' was expected.
// K2_ERROR: Cannot infer type for type parameter 'T'. Specify it explicitly.

fun bar(list: MutableList<Int>) {}

fun foo() {
    bar(emptyList<caret>())
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ReplaceWithMutableCollectionFactoryFix