// "Wrap element with 'listOf()' call" "true"
// WITH_STDLIB
fun test(ints: List<Int>, i: Int) {
    when (ints) {
        <caret>i -> {}
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithCollectionLiteralCallFix