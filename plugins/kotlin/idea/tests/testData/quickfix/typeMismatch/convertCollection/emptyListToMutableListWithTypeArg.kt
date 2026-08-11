// "Replace 'emptyList<Int>()' with 'mutableListOf<Int>()'" "true"
// PRIORITY: HIGH
// K2_ERROR: RETURN_TYPE_MISMATCH

fun foo(): MutableList<Int> {
    return emptyList<Int>()<caret>
}


// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ReplaceWithMutableCollectionFactoryFix