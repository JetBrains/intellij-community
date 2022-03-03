// "Remove 'val' from parameter" "true"
// WITH_STDLIB
fun f() {
    try {

    } catch (<caret>val e: Exception) {

    }
}