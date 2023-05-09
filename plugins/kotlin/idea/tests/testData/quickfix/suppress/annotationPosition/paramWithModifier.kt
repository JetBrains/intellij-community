// "Suppress 'DIVISION_BY_ZERO' for parameter p" "true"

fun foo(p: Int = 2 / <caret>0) = null
