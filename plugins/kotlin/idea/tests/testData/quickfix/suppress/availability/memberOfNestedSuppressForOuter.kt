// "Suppress 'DIVISION_BY_ZERO' for class C" "true"

class C {
    class D {
        fun foo() = 2 / <caret>0
    }
}
