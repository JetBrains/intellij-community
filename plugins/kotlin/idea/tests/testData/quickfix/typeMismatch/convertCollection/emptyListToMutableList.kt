// "Replace 'emptyList()' with 'mutableListOf()'" "true"
// PRIORITY: HIGH
// K2_ERROR: Cannot infer type for type parameter 'T'. Specify it explicitly.
// K2_ERROR: Return type mismatch: expected 'MutableList<Int>', actual 'List<uninferred ??? (Unknown type for type parameter T)>'.

fun foo(): MutableList<Int> {
    return emptyList<caret>()
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ReplaceWithMutableCollectionFactoryFix