// "Replace 'emptySet()' with 'mutableSetOf()'" "true"
// PRIORITY: HIGH
// K2_ERROR: ASSIGNMENT_TYPE_MISMATCH
// K2_ERROR: CANNOT_INFER_PARAMETER_TYPE

fun foo() {
    var set: MutableSet<Double>
    set =<caret> emptySet()
}


// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ReplaceWithMutableCollectionFactoryFix