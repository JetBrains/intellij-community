// "Convert expression to 'Byte'" "true"
// K2_ERROR: INCOMPATIBLE_TYPES
fun test(b: Byte, i: Int) {
    when (b) {
        <caret>i -> {}
    }
}
// IGNORE_K2
// Task for K2: KTIJ-33283