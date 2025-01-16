// "Change type from 'Int' to 'X'" "true"
inline fun <reified T> inlineReified(f: (T) -> T) = {}

inline fun <reified X> callInlineReified() = inlineReified<X> { x: <caret>Int ->
    x
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeTypeFix
// IGNORE_K2