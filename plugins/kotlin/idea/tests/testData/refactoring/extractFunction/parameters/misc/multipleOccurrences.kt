// PARAM_TYPES: kotlin.Int
// PARAM_TYPES: kotlin.Int
// PARAM_TYPES: kotlin.Int
// PARAM_DESCRIPTOR: value-parameter a: kotlin.Int defined in foo
// PARAM_DESCRIPTOR: value-parameter b: kotlin.Int defined in foo
// PARAM_DESCRIPTOR: value-parameter c: kotlin.Int defined in foo
// SIBLING:
fun foo(a: Int, b: Int, c: Int): Int {
    return <selection>(a + b*a - c) + b*c</selection>
}
