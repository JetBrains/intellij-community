// "Replace 'emptyMap()' with 'mutableMapOf()'" "true"
// PRIORITY: HIGH

fun foo(): MutableMap<String, Int> {
    return emptyMap<caret>()
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ReplaceWithMutableCollectionFactoryFix