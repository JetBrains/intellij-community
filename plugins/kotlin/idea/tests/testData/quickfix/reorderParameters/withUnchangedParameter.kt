// "Reorder parameters" "true"
// WITH_STDLIB
fun foo(
    c1: String,
    a: List<List<String>> = listOf(listOf(b<caret>)),
    b: String,
) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReorderParametersFix