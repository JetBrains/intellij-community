// PARAM_TYPES: kotlin.Int
// PARAM_TYPES: kotlin.Int
// PARAM_DESCRIPTOR: value-parameter a: kotlin.Int defined in B.<init>
// PARAM_DESCRIPTOR: value-parameter b: kotlin.Int defined in B.<init>
open class A(a: Int, b: Int)

class B(a: Int, b: Int): A(<selection>a + b</selection>, a - b)