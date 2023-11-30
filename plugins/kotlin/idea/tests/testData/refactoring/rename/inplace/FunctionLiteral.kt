// NEW_NAME: y
// RENAME: variable
fun f() {
    val f: (Int) -> Int = { <caret>x -> x + x }
}