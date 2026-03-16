// "Replace 'listOf(…)' with 'mutableListOf(…)'" "true"
// PRIORITY: HIGH
// K2_ERROR: Return type mismatch: expected 'MutableList<List<String>>', actual 'List<List<String>>'.
fun foo(): MutableList<List<String>> {
    return list<caret>Of(listOf(""))
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ReplaceWithMutableCollectionFactoryFix