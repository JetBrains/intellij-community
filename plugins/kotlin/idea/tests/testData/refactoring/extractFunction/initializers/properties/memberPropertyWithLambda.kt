// PARAM_TYPES: kotlin.Int
// PARAM_DESCRIPTOR: value-parameter b: kotlin.Int defined in A.`<init>`
class A(val a: Int, b: Int) {
    val foo = { <selection>a + b</selection> - 1 }.invoke()
}