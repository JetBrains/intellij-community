// "Convert expression to 'Byte'" "true"
fun test(b: Byte, i: Int) {
    when (b) {
        <caret>i -> {}
    }
}