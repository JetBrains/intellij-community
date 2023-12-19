// "Suppress 'UNUSED_ANONYMOUS_PARAMETER' for parameter a" "false"
// ACTION: Convert to anonymous function
// ACTION: Convert to lazy property
// ACTION: Convert to single-line lambda
// ACTION: Enable 'Types' inlay hints
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Remove explicit lambda parameter types (may break code)
// ACTION: Rename to _

val x = { <caret>a: Int ->
    5
}

// IGNORE_K2
