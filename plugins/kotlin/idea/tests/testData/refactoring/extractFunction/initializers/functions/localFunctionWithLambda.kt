// PARAM_TYPES: kotlin.Int
// PARAM_TYPES: kotlin.Int
// PARAM_DESCRIPTOR: value-parameter a: kotlin.Int defined in bar.foo
// PARAM_DESCRIPTOR: value-parameter b: kotlin.Int defined in bar.foo
fun bar(n: Int) {
    fun foo(a: Int, b: Int) = { <selection>a + b - n</selection> - 1 }.invoke()
}