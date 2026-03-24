// "Replace 'listOf(1, 2)' with 'mutableListOf(1, 2)'" "true"
// K2_ERROR: Return type mismatch: expected 'MutableList<Int>', actual 'List<Int>'.

fun foo(): MutableList<Int> {
    return listOf<caret>(1, 2)
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ReplaceWithMutableCollectionFactoryFix