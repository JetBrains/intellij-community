// "Suppress 'DIVISION_BY_ZERO' for fun foo" "true"
// ERROR: Unresolved reference: ann

@ann fun foo() = 2 / <caret>0
