// "Replace 'listOf(1)' with 'mutableListOf(1)'" "true"
// PRIORITY: HIGH
// K2_ERROR: Return type mismatch: expected 'MutableList<out Number>', actual 'List<Int>'.

fun foo(): MutableList<out Number> {
    return list<caret>Of(1)
}
// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ReplaceWithMutableCollectionFactoryFix