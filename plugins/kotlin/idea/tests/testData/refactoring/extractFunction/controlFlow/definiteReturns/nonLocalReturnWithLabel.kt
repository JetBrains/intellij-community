// WITH_STDLIB
// PARAM_DESCRIPTOR: value-parameter it: kotlin.Int defined in foo.`<anonymous>`
// PARAM_TYPES: kotlin.Int
fun foo(): Int {
    1.let { <selection>return@foo it + 1</selection> }
}