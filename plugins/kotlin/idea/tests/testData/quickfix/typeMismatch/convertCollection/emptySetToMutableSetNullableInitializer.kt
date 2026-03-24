// "Replace 'emptySet()' with 'mutableSetOf()'" "true"
// PRIORITY: HIGH
// K2_ERROR: Cannot infer type for type parameter 'T'. Specify it explicitly.
// K2_ERROR: Initializer type mismatch: expected 'MutableSet<Int>?', actual 'Set<uninferred ??? (Unknown type for type parameter T)>'.

fun foo() {
    val set: MutableSet<Int>? =<caret> emptySet()
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ReplaceWithMutableCollectionFactoryFix
