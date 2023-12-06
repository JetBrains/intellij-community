// "Surround with null check" "true"
// WITH_STDLIB

fun foo(list: List<String>?) {
    for (element in <caret>list) {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithNullCheckFix