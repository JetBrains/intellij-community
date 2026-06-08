// "Replace 'setOf(…)' with 'mutableSetOf(…)'" "true"
// K2_ERROR: Return type mismatch: expected 'MutableSet<String>', actual 'Set<String>'.

fun bar(): MutableSet<String> {
    return setOf<caret>("a", "b")
}


// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ReplaceWithMutableCollectionFactoryFix