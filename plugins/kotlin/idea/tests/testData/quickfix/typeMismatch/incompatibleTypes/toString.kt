// "Add 'toString()' call" "true"
fun test(s: String, i: Int) {
    when (s) {
        <caret>i -> {}
    }
}