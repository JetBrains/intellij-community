// "Change type from 'Int' to 'X'" "true"
// K2_ERROR: EXPECTED_PARAMETER_TYPE_MISMATCH
// K2_ERROR: RETURN_TYPE_MISMATCH
inline fun <reified T> inlineReified(f: (T) -> T) = {}

inline fun <reified X> callInlineReified() = inlineReified<X> { x: <caret>Int ->
    x
}
// IGNORE_K2