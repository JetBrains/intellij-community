// "Replace 'emptyList()' with 'mutableListOf()'" "true"
// PRIORITY: HIGH

fun foo() {
    val list: MutableList<String> =<caret> emptyList()
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ReplaceWithMutableCollectionFactoryFix