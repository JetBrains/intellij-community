// "Suppress 'DIVISION_BY_ZERO' for class D" "true"

class C {
    class D {
        fun foo() = 2 / <caret>0
    }
}
