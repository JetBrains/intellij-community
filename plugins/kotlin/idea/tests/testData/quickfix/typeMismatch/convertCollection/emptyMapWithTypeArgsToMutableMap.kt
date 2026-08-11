// "Replace 'emptyMap<String, Int>()' with 'mutableMapOf<String, Int>()'" "true"
// PRIORITY: HIGH
// K2_ERROR: RETURN_TYPE_MISMATCH

fun foo(): MutableMap<String, Int> {
    return emptyMap<String, Int><caret>()
}


// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ReplaceWithMutableCollectionFactoryFix