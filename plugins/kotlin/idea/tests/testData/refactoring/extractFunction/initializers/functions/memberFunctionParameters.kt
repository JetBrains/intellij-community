// PARAM_TYPES: kotlin.Int
// PARAM_DESCRIPTOR: value-parameter a: kotlin.Int defined in A.foo
class A(val n: Int) {
    fun foo(a: Int, b: Int = <selection>a + n</selection>) = a + b - n - 1
}