// "Replace with dot call" "true"
// WITH_STDLIB
fun foo(a: String) {
    val b = a // comment1
            // comment2
            ?.<caret>length
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithDotCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithDotCallFix