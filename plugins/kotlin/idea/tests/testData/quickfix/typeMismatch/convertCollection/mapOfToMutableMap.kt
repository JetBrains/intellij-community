// "Replace 'mapOf(…)' with 'mutableMapOf(…)'" "true"

fun baz(): MutableMap<String, Int> {
    return mapOf<caret>("key" to 1)
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ReplaceWithMutableCollectionFactoryFix