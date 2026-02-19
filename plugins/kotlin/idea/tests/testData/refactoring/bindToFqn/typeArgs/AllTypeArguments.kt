// BIND_TO C.D
// BIND_RESULT "C<Z>.D<X, Y>"
class A<Z> {
    class B<X, Y>
}

class C<Z> {
    class D<X, Y>
}

typealias E<Z, X, Y> = A<Z>.<caret>B<X, Y>