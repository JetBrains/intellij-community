// PARAM_TYPES: kotlin.Int
// PARAM_DESCRIPTOR: value-parameter a: kotlin.Int defined in foo
// SIBLING:
fun foo(a: Int, b: Int): Int {
    <selection>if (a > 0) return a + b</selection>
    return 0
}