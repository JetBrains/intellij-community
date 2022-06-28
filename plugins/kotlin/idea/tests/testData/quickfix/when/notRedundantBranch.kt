// "Remove branch" "false"
// ACTION: Add braces to 'when' entry
// ACTION: Add braces to all 'when' entries
// ACTION: Do not show return expression hints
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Remove condition
fun test(x: Int): String {
    return when (x) {
        1 -> "1"
        2 -> "2"
        <caret>null, 3 -> "3"
        else -> ""
    }
}