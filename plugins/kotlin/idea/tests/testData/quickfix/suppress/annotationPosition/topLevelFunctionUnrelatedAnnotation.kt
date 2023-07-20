// "Suppress 'DIVISION_BY_ZERO' for fun foo" "true"

@ann fun foo() = 2 / <caret>0

annotation class ann
