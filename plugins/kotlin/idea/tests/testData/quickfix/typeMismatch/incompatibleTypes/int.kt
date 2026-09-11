// "Convert expression to 'Int'" "true"
// K2_ERROR: INCOMPATIBLE_TYPES
fun test(b: Byte, i: Int) {
    when (i) {
        <caret>b -> {}
    }
}
// IGNORE_K2
// Task for K2: KTIJ-33283