// WITH_STDLIB
// SUGGESTED_NAMES: function, getY
// PARAM_DESCRIPTOR: val x: (() -> kotlin.Unit).() -> kotlin.Unit defined in extentionInSignature
// PARAM_TYPES: (() -> kotlin.Unit).() -> kotlin.Unit
fun extentionInSignature() {
    val x : (() -> Unit).() -> Unit = {}
    val y = <selection>x</selection>
}