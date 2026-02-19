// BIND_TO D.E.F
// BIND_RESULT "D<Z, X>.E.F<Y>"
class A<X, Y> {
    class B {
        class C<Z> {

        }
    }
}

class D<X, Y> {
    class E {
        class F<Z> {

        }
    }
}

typealias G<Z, X, Y> = A<Z, X>.B.<caret>C<Y>