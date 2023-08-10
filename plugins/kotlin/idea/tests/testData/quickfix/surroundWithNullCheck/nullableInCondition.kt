// "Surround with null check" "true"
// WITH_STDLIB

fun foz(arg: String?) {
    if (arg<caret>.isNotEmpty()) {

    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithNullCheckFix