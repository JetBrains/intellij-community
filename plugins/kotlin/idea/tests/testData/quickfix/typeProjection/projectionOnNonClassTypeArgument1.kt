// "Remove 'out' modifier" "true"
fun <T> foo(x : T) {}

fun bar() {
    foo<<caret>out Int>(44)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase