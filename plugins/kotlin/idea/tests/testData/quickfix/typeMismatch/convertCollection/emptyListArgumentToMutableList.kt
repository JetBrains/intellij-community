// "Replace 'emptyList()' with 'mutableListOf()'" "true"
// PRIORITY: HIGH

fun bar(list: MutableList<Int>) {}

fun foo() {
    bar(emptyList<caret>())
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ReplaceWithMutableCollectionFactoryFix