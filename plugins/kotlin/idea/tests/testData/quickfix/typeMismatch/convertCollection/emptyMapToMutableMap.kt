// "Replace 'emptyMap()' with 'mutableMapOf()'" "true"
// PRIORITY: HIGH
// K2_ERROR: Cannot infer type for type parameter 'K'. Specify it explicitly.
// K2_ERROR: Cannot infer type for type parameter 'V'. Specify it explicitly.
// K2_ERROR: Return type mismatch: expected 'MutableMap<String, Int>', actual 'Map<uninferred ??? (Unknown type for type parameter K), uninferred ??? (Unknown type for type parameter V)>'.

fun foo(): MutableMap<String, Int> {
    return emptyMap<caret>()
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ReplaceWithMutableCollectionFactoryFix