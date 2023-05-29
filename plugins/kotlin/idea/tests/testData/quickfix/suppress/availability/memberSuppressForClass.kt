// "Suppress 'DIVISION_BY_ZERO' for class C" "true"

class C {
    fun foo() = 2 / <caret>0
}
