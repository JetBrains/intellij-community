// PARAM_TYPES: A<T>.B<U>
// PARAM_TYPES: V, Data
// PARAM_DESCRIPTOR: public final inner class B<U : Data> defined in A
// PARAM_DESCRIPTOR: value-parameter v: V defined in A.B.foo
open class Data(val x: Int)

class A<T: Data>(val t: T) {
    // SIBLING:
    inner class B<U: Data>(val u: U) {
        fun <V: Data> foo(v: V): Int {
            return <selection>t.x + u.x + v.x</selection>
        }
    }
}