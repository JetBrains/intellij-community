// "Replace 'emptySet()' with 'mutableSetOf()'" "true"
// PRIORITY: HIGH

fun foo() {
    var set: MutableSet<Double>
    set =<caret> emptySet()
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ReplaceWithMutableCollectionFactoryFix