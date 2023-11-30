// NEW_NAME: e1
// RENAME: variable
fun f() {
    try {
    }
    catch (<caret>e: Exception) {
        println(e)
    }
}