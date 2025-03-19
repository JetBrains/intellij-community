// "Convert expression to 'Set' by inserting '.toSet()'" "true"
// WITH_STDLIB

fun foo(a: List<String>) {
    val s: Set<String>
    s = a<caret>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertCollectionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertCollectionFix