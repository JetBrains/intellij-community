// "Convert expression to 'Int'" "true"
fun test(b: Byte, i: Int) {
    when (i) {
        <caret>b -> {}
    }
}