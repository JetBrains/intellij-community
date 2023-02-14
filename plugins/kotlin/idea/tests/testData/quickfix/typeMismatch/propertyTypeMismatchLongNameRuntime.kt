// "Change type of 'f' to '(Delegates) -> Unit'" "true"
// WITH_STDLIB

fun foo() {
    var f: Int = { x: kotlin.properties.Delegates ->  }<caret>
}