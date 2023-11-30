// NEW_NAME: w
// RENAME: member
fun foo(x: Int) {
    val <caret>y = x + 1
    val z = y + 1
    // y
    val s = "y"
}