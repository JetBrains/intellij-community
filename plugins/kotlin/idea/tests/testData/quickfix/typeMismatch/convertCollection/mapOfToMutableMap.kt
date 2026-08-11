// "Replace 'mapOf(…)' with 'mutableMapOf(…)'" "true"
// K2_ERROR: RETURN_TYPE_MISMATCH

fun baz(): MutableMap<String, Int> {
    return mapOf<caret>("key" to 1)
}


// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ReplaceWithMutableCollectionFactoryFix