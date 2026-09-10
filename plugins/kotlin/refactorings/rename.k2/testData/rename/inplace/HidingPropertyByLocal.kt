// NEW_NAME: b
// RENAME: member
package rename

val <caret>a = ""
fun m() {
    val b = ""
    val c = a + b
}