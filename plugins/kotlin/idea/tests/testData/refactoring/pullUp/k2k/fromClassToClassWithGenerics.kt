// WITH_RUNTIME
interface I

interface Z<T>

open class A<T: I, U: I, V>

class C<W: I> {
    inner class <caret>B<X: I, Y>(x: X, y: Y): A<X, I, Z<Y>>() {
        // INFO: {"checked": "true"}
        fun <S> foo(x1: X, x2: Z<X>, y1: Y, y2: Z<Y>, w1: W, w2: Z<W>, s1: S, s2: Z<S>) {

        }
        // INFO: {"checked": "true"}
        inner class Foo<S>(x1: X, x2: Z<X>, y1: Y, y2: Z<Y>, w1: W, w2: Z<W>, s1: S, s2: Z<S>): A<X, I, Z<Y>>(), Z<W> {

        }
        // INFO: {"checked": "true"}
        val foo1: X
        // INFO: {"checked": "true"}
        val foo2: Z<X>
        // INFO: {"checked": "true"}
        val foo3: Y
        // INFO: {"checked": "true"}
        val foo4: Z<Y>

        // INFO: {"checked": "true", "toAbstract": "true"}
        val foo5 = x
        // INFO: {"checked": "true", "toAbstract": "true"}
        val foo6 = object: Z<X> {}
        // INFO: {"checked": "true", "toAbstract": "true"}
        val foo7 = y
        // INFO: {"checked": "true", "toAbstract": "true"}
        val foo8 = object: Z<Y> {}
    }
}