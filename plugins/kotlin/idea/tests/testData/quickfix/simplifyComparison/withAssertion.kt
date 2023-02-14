// "Simplify comparison" "true"
// WITH_STDLIB
fun test() {
    val s = ""
    assert(<caret>s != null)
}