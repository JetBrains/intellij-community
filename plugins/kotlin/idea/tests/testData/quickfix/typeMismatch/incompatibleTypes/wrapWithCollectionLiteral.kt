// "Wrap element with 'listOf()' call" "true"
// WITH_STDLIB
// K2_ERROR: Incompatible types 'List<Int>' and 'Int'.
fun test(ints: List<Int>, i: Int) {
    when (ints) {
        <caret>i -> {}
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithCollectionLiteralCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithCollectionLiteralCallFix