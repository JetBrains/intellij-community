// "Change type of 'f' to '(Long) -> Unit'" "true"
fun foo() {
    var f: Int = if (true) { x: Long ->  }<caret> else { x: Long ->  }
}