// "Suppress 'DIVISION_BY_ZERO' for interface C" "true"

interface C {
    fun foo() = 2 / <caret>0
}
