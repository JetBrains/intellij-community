// PARAM_TYPES: kotlin.Int
// PARAM_TYPES: kotlin.Int
// PARAM_DESCRIPTOR: value-parameter a: kotlin.Int defined in foo
// PARAM_DESCRIPTOR: val b: kotlin.Int defined in foo
// SIBLING:
fun foo(a: Int): Int {
    val b: Int = 1
    <selection>when (a + b) {
        0 -> return 0
        1 -> println(1)
        else -> println(2)
    }
    </selection>
    return 1
}