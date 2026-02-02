// "Replace 'emptyList<Int>()' with 'mutableListOf<Int>()'" "true"
// PRIORITY: HIGH

fun foo(): MutableList<Int> {
    return emptyList<Int>()<caret>
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ReplaceWithMutableCollectionFactoryFix