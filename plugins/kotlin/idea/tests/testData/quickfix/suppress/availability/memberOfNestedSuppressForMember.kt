// "Suppress 'DIVISION_BY_ZERO' for fun foo" "true"

class C {
    class D {
        fun foo() = 2 / <caret>0
    }
}
