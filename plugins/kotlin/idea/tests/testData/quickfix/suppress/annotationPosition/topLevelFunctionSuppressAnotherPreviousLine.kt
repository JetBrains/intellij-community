// "Suppress 'DIVISION_BY_ZERO' for fun foo" "true"

@Suppress("FOO")
fun foo() = 2 / <caret>0
